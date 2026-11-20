package gov.medicaid.pem.kit.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The "slow service" of the timeout tests: a server that accepts every connection and then says
 * nothing, so a client that has no read limit would wait for ever. It listens on the loopback
 * address only, on a free port, and keeps the connections open until it is closed.
 */
public final class SilentServer implements AutoCloseable {

  private final ServerSocket socket;
  private final List<Socket> held = new CopyOnWriteArrayList<>();
  private final CountDownLatch firstConnection = new CountDownLatch(1);

  public SilentServer() throws IOException {
    socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    Thread acceptor = new Thread(this::acceptLoop, "silent-server-" + socket.getLocalPort());
    acceptor.setDaemon(true);
    acceptor.start();
  }

  private void acceptLoop() {
    try {
      while (!socket.isClosed()) {
        held.add(socket.accept());
        firstConnection.countDown();
      }
    } catch (IOException closed) {
      // the test closed the server
    }
  }

  /** The base URL, for example {@code http://127.0.0.1:49152}. */
  public String url() {
    return "http://127.0.0.1:" + socket.getLocalPort();
  }

  /** True when a client has connected (and so the server held the connection without answering). */
  public boolean acceptedAConnection() throws InterruptedException {
    return firstConnection.await(2, TimeUnit.SECONDS);
  }

  @Override
  public void close() throws IOException {
    socket.close();
    for (Socket connection : held) {
      connection.close();
    }
  }
}
