package micheal65536.vienna.objectstore.client;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ObjectStoreClient
{
	@NotNull
	public static ObjectStoreClient create(@NotNull String connectionString) throws ConnectException
	{
		String[] parts = connectionString.split(":", 2);
		String host = parts[0];

		int port;
		try
		{
			port = parts.length > 1 ? Integer.parseInt(parts[1]) : 5396;
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException(
					"Invalid port number \"%s\"".formatted(parts[1])
			);
		}

		if (port <= 0 || port > 65535)
		{
			throw new IllegalArgumentException("Port number out of range");
		}

		/*
		 * Wait for the Object Store server to become available.
		 *
		 * The server may still be starting when the API server
		 * attempts to connect. Retry for up to 30 seconds.
		 */
		long deadline = System.nanoTime()
				+ TimeUnit.SECONDS.toNanos(30);

		IOException lastException = null;

		while (System.nanoTime() < deadline)
		{
			try
			{
				Socket socket = new Socket();
				socket.connect(
						new InetSocketAddress(host, port),
						1000
				);

				return new ObjectStoreClient(socket);
			}
			catch (IOException exception)
			{
				lastException = exception;

				try
				{
					Thread.sleep(250);
				}
				catch (InterruptedException interruptedException)
				{
					Thread.currentThread().interrupt();

					throw new ConnectException(
							"Interrupted while waiting for object store",
							interruptedException
					);
				}
			}
		}

		throw new ConnectException(
				"Could not connect to object store within 30 seconds",
				lastException
		);
	}

	public static final class ConnectException extends ObjectStoreClientException
	{
		private ConnectException(String message)
		{
			super(message);
		}

		private ConnectException(String message, Throwable cause)
		{
			super(message, cause);
		}
	}

	private final Socket socket;
	private final LinkedBlockingQueue<Object> outgoingMessageQueue =
			new LinkedBlockingQueue<>();

	private final Thread outgoingThread;
	private final Thread incomingThread;

	private final ReentrantLock lock = new ReentrantLock(true);

	private boolean closed = false;

	private Command currentCommand = null;
	private final LinkedList<Command> queuedCommands = new LinkedList<>();

	private ObjectStoreClient(@NotNull Socket socket)
	{
		this.socket = socket;

		this.outgoingThread = new Thread(() ->
		{
			try (OutputStream outputStream = this.socket.getOutputStream())
			{
				for (; ; )
				{
					Object message = this.outgoingMessageQueue.take();

					if (message instanceof String command)
					{
						outputStream.write(
								command.getBytes(StandardCharsets.US_ASCII)
						);
					}
					else if (message instanceof byte[] data)
					{
						outputStream.write(data);
					}
					else
					{
						throw new AssertionError();
					}

					outputStream.flush();
				}
			}
			catch (InterruptedException exception)
			{
				// Thread interrupted while waiting for a message.
			}
			catch (IOException exception)
			{
				this.lock.lock();
				try
				{
					this.closed = true;
				}
				finally
				{
					this.lock.unlock();
				}
			}

			this.initiateClose();
		});

		this.incomingThread = new Thread(() ->
		{
			try (InputStream inputStream = this.socket.getInputStream())
			{
				byte[] readBuffer = new byte[65536];

				ByteArrayOutputStream byteArrayOutputStream =
						new ByteArrayOutputStream(128);

				String lastMessage = null;
				int binaryReadLength = 0;

				for (; ; )
				{
					this.lock.lock();

					try
					{
						if (this.closed)
						{
							break;
						}
					}
					finally
					{
						this.lock.unlock();
					}

					int readLength = inputStream.read(readBuffer);

					if (readLength > 0)
					{
						int startOffset = 0;

						while (startOffset < readLength)
						{
							this.lock.lock();

							try
							{
								if (this.closed)
								{
									break;
								}
							}
							finally
							{
								this.lock.unlock();
							}

							if (binaryReadLength > 0)
							{
								if (startOffset + binaryReadLength > readLength)
								{
									int length = readLength - startOffset;

									byteArrayOutputStream.write(
											readBuffer,
											startOffset,
											length
									);

									binaryReadLength -= length;
									startOffset += length;
								}
								else
								{
									byteArrayOutputStream.write(
											readBuffer,
											startOffset,
											binaryReadLength
									);

									if (!this.handleBinaryData(
											lastMessage,
											byteArrayOutputStream.toByteArray()
									))
									{
										this.initiateClose();
										break;
									}

									lastMessage = null;
									byteArrayOutputStream =
											new ByteArrayOutputStream(128);

									startOffset += binaryReadLength;
									binaryReadLength = 0;
								}
							}
							else
							{
								for (
										int offset = startOffset;
										offset < readLength;
										offset++
								)
								{
									if (readBuffer[offset] == '\n')
									{
										byteArrayOutputStream.write(
												readBuffer,
												startOffset,
												offset - startOffset
										);

										lastMessage =
												byteArrayOutputStream.toString(
														StandardCharsets.US_ASCII
												);

										binaryReadLength =
												this.handleMessage(lastMessage);

										if (binaryReadLength == -1)
										{
											this.initiateClose();
											break;
										}

										byteArrayOutputStream =
												new ByteArrayOutputStream(128);

										startOffset = offset + 1;
										break;
									}
									else if (offset == readLength - 1)
									{
										byteArrayOutputStream.write(
												readBuffer,
												startOffset,
												readLength - startOffset
										);

										startOffset = readLength;
									}
								}
							}
						}
					}
					else if (readLength == -1)
					{
						this.initiateClose();
					}
					else
					{
						throw new AssertionError();
					}
				}
			}
			catch (IOException exception)
			{
				this.lock.lock();

				try
				{
					this.closed = true;
				}
				finally
				{
					this.lock.unlock();
				}
			}

			this.initiateClose();

			this.lock.lock();

			try
			{
				if (this.currentCommand != null)
				{
					this.currentCommand.completableFuture.complete(
							this.currentCommand.type == Command.Type.DELETE
									? false
									: null
					);

					this.currentCommand = null;
				}

				this.queuedCommands.forEach(command ->
						command.completableFuture.complete(
								command.type == Command.Type.DELETE
										? false
										: null
						)
				);

				this.queuedCommands.clear();
			}
			finally
			{
				this.lock.unlock();
			}
		});

		this.outgoingThread.start();
		this.incomingThread.start();
	}

	public void close()
	{
		this.initiateClose();

		for (; ; )
		{
			try
			{
				this.incomingThread.join();
				break;
			}
			catch (InterruptedException exception)
			{
				// Keep waiting for the thread to terminate.
			}
		}

		for (; ; )
		{
			try
			{
				this.outgoingThread.join();
				break;
			}
			catch (InterruptedException exception)
			{
				// Keep waiting for the thread to terminate.
			}
		}
	}

	private void initiateClose()
	{
		this.lock.lock();

		try
		{
			this.closed = true;
		}
		finally
		{
			this.lock.unlock();
		}

		try
		{
			this.socket.close();
		}
		catch (IOException exception)
		{
			// Ignore socket close failure.
		}

		this.outgoingThread.interrupt();
	}

	@NotNull
	public CompletableFuture<String> store(byte[] data)
	{
		CompletableFuture<String> completableFuture =
				new CompletableFuture<>();

		this.queueCommand(
				new Command(
						Command.Type.STORE,
						data,
						completableFuture
				)
		);

		return completableFuture;
	}

	@NotNull
	public CompletableFuture<byte[]> get(@NotNull String id)
	{
		CompletableFuture<byte[]> completableFuture =
				new CompletableFuture<>();

		this.queueCommand(
				new Command(
						Command.Type.GET,
						id,
						completableFuture
				)
		);

		return completableFuture;
	}

	@NotNull
	public CompletableFuture<Boolean> delete(@NotNull String id)
	{
		CompletableFuture<Boolean> completableFuture =
				new CompletableFuture<>();

		this.queueCommand(
				new Command(
						Command.Type.DELETE,
						id,
						completableFuture
				)
		);

		return completableFuture;
	}

	private void queueCommand(@NotNull Command command)
	{
		this.lock.lock();

		try
		{
			if (this.closed)
			{
				command.completableFuture.complete(
						command.type == Command.Type.DELETE
								? false
								: null
				);
			}
			else
			{
				this.queuedCommands.add(command);

				if (this.currentCommand == null)
				{
					this.sendNextCommand();
				}
			}
		}
		finally
		{
			this.lock.unlock();
		}
	}

	private void sendNextCommand()
	{
		this.lock.lock();

		try
		{
			this.currentCommand = null;

			if (this.closed)
			{
				return;
			}

			if (!this.queuedCommands.isEmpty())
			{
				this.currentCommand =
						this.queuedCommands.removeFirst();

				switch (this.currentCommand.type)
				{
					case STORE ->
					{
						this.sendMessage(
								"STORE "
										+ Integer.toString(
												((byte[]) this.currentCommand.data).length
										)
										+ "\n"
						);

						this.sendMessage(
								this.currentCommand.data
						);
					}

					case GET ->
					{
						this.sendMessage(
								"GET "
										+ this.currentCommand.data
										+ "\n"
						);
					}

					case DELETE ->
					{
						this.sendMessage(
								"DEL "
										+ this.currentCommand.data
										+ "\n"
						);
					}
				}
			}
		}
		finally
		{
			this.lock.unlock();
		}
	}

	private int handleMessage(@NotNull String message)
	{
		this.lock.lock();

		try
		{
			if (this.closed)
			{
				return -1;
			}

			if (this.currentCommand == null)
			{
				return -1;
			}

			String[] parts = message.split(" ", 2);

			switch (this.currentCommand.type)
			{
				case STORE ->
				{
					if (parts[0].equals("OK"))
					{
						if (parts.length != 2)
						{
							return -1;
						}

						this.currentCommand.completableFuture.complete(
								parts[1]
						);

						this.sendNextCommand();

						return 0;
					}
					else if (parts[0].equals("ERR"))
					{
						this.currentCommand.completableFuture.complete(null);
						this.sendNextCommand();

						return 0;
					}

					return -1;
				}

				case GET ->
				{
					if (parts[0].equals("OK"))
					{
						if (parts.length != 2)
						{
							return -1;
						}

						try
						{
							int length = Integer.parseInt(parts[1]);

							if (length < 0)
							{
								return -1;
							}

							if (length == 0)
							{
								this.currentCommand.completableFuture.complete(
										new byte[0]
								);

								this.sendNextCommand();

								return 0;
							}

							return length;
						}
						catch (NumberFormatException exception)
						{
							return -1;
						}
					}
					else if (parts[0].equals("ERR"))
					{
						this.currentCommand.completableFuture.complete(null);
						this.sendNextCommand();

						return 0;
					}

					return -1;
				}

				case DELETE ->
				{
					if (parts[0].equals("OK"))
					{
						this.currentCommand.completableFuture.complete(true);
						this.sendNextCommand();

						return 0;
					}
					else if (parts[0].equals("ERR"))
					{
						this.currentCommand.completableFuture.complete(false);
						this.sendNextCommand();

						return 0;
					}

					return -1;
				}

				default ->
				{
					throw new AssertionError();
				}
			}
		}
		finally
		{
			this.lock.unlock();
		}
	}

	private boolean handleBinaryData(
			@NotNull String message,
			byte[] data
	)
	{
		this.lock.lock();

		try
		{
			if (this.closed)
			{
				return false;
			}

			if (this.currentCommand == null)
			{
				throw new AssertionError();
			}

			String[] parts = message.split(" ", 2);

			if (parts.length != 2)
			{
				throw new AssertionError();
			}

			switch (this.currentCommand.type)
			{
				case GET ->
				{
					if (parts[0].equals("OK"))
					{
						this.currentCommand.completableFuture.complete(data);
						this.sendNextCommand();

						return true;
					}

					throw new AssertionError();
				}

				default ->
				{
					throw new AssertionError();
				}
			}
		}
		finally
		{
			this.lock.unlock();
		}
	}

	private void sendMessage(@NotNull Object message)
	{
		this.lock.lock();

		try
		{
			if (this.closed)
			{
				throw new AssertionError();
			}
		}
		finally
		{
			this.lock.unlock();
		}

		for (; ; )
		{
			try
			{
				this.outgoingMessageQueue.put(message);
				break;
			}
			catch (InterruptedException exception)
			{
				// Keep waiting unless the client is being closed.
				if (this.closed)
				{
					return;
				}
			}
		}
	}

	private static class Command
	{
		public final Type type;
		public final Object data;
		public final CompletableFuture completableFuture;

		public enum Type
		{
			STORE,
			GET,
			DELETE
		}

		public Command(
				Type type,
				Object data,
				CompletableFuture completableFuture
		)
		{
			this.type = type;
			this.data = data;
			this.completableFuture = completableFuture;
		}
	}
}
