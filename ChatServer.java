import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.lang.reflect.Field;


public class ChatServer
{
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();


  static private String messageFromClient = "";

  static private Selector selector;

  static private ArrayList<String> nicks = new ArrayList<String>(); // save used nicks


  // Client states
  static private final int init = 0;
  static private final int inside = 1;
  static private final int outside = 2;

  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );

    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port );

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if (key.isAcceptable()) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from "+s );


            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, new Client("",init,""));


          } else if (key.isReadable()) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              boolean ok = processInput( sc, key );


              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                Client cl = getClient(key);
                if(cl.state == inside) {
                  sendToOthers("LEFT " + cl.nick +"\n",cl);
                }
                nicks.remove(cl.nick);
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s );
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } catch( IOException ie ) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }


  // Just read the message from the socket and send it to stdout
  static private boolean processInput( SocketChannel sc, SelectionKey key ) throws IOException {

    boolean ok = true;
    // Read the message to the buffer
    buffer.clear();
    int bytes = sc.read( buffer );
    buffer.flip();

    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
    }

    for(int i=0; i<bytes; i++) {
      byte cur = buffer.get(i);
      if(cur == 10) {
        System.out.println(messageFromClient);
        ok = selectOutput(messageFromClient,sc, key);
        messageFromClient = "";
        break;
      }
      byte[] aux = new byte[1];
      aux[0] = cur;
      String auxStr = new String(aux, StandardCharsets.UTF_8);;
      messageFromClient += auxStr;
    }
    return ok;
  }

  static private boolean selectOutput(String message, SocketChannel sc, SelectionKey key) throws IOException{
    String[] commands = message.split(" ");
    String command = commands[0];

    if(command.equals("/nick")) {
      handleNick(commands[1], sc, key);
    }
    else if(command.equals("/join")) {
      handleJoin(commands[1], sc, key);
    }
    else if(command.equals("/leave")) {
      handleLeave(sc, key);
    }
    else if(command.equals("/bye")) {
      sendToOne("BYE\n", sc);
      return false;
    }
    else if(command.equals("/priv")) {

      String newMessage = "";
      System.out.println("heyeyey");
      int i;
      for(i=2; i<commands.length-1; i++) {
       newMessage += commands[i]+" ";
      }

      newMessage += commands[i];

      handlePriv(commands[1], newMessage, sc, key);
    }
    else
      handleMessage(message, sc, key);
      
    return true;
  }

  static private void handleNick(String newNick, SocketChannel sc, SelectionKey key) throws IOException{
    Client cl = getClient(key);

    if(nicks.contains(newNick)) {
      sendToOne("ERROR\n",sc);
    } else {
        if(cl.state == outside || cl.state == inside) {
          nicks.remove(cl.nick);
        }

        nicks.add(newNick);

        if(cl.state == outside || cl.state == init) {
          key.attach(new Client(newNick,outside,cl.room));
        } else {
          key.attach(new Client(newNick,inside,cl.room));
        }

        sendToOne("OK\n",sc);
        
        if(cl.state == inside) {
          sendToOthers("NEWNICK " + cl.nick + " " + newNick + "\n", new Client(newNick,inside,cl.room));
        }
    }
  }

  static private void handleJoin(String newRoom, SocketChannel sc, SelectionKey key) throws IOException{
    Client cl = getClient(key);
    Client cl1 = new Client(cl.nick, inside, newRoom);

    if(cl.state == outside) {
      key.attach(cl1);
      sendToOne("OK\n",sc);
      sendToOthers("JOINED " + cl1.nick +"\n",cl1);
  } else if(cl.state == inside) {
      key.attach(cl1);
      sendToOne("OK\n",sc);
      sendToOthers("LEFT " + cl.nick +"\n",cl);
      sendToOthers("JOINED " + cl1.nick + "\n",cl1);
  } else {
      sendToOne("ERROR\n",sc);
    }
  }

  static private void handleLeave(SocketChannel sc, SelectionKey key) throws IOException {
    Client cl = getClient(key);

    if(cl.state == inside) {
      sendToOne("OK\n",sc);
      sendToOthers("LEFT " + cl.nick + "\n",cl);
      key.attach(new Client(cl.nick, outside, ""));
    } else {
       sendToOne("ERROR\n",sc);
    }
  }

  
  static private void handleMessage(String message, SocketChannel sc, SelectionKey key) throws IOException{
    Client cl = getClient(key);

    if(message.charAt(0) == '/') {
      message = message.substring(1);
    }

    if(cl.state == inside) {
      sendToOne("MESSAGE " + cl.nick + " " + message + "\n", sc);
      sendToOthers("MESSAGE " + cl.nick + " " + message + "\n", cl);
    } else {
      sendToOne("ERROR\n", sc);
    }
  }

  static private void handlePriv(String nick, String message, SocketChannel sc, SelectionKey key) throws IOException{
    Client cl = getClient(key);



    if(nicks.contains(nick)) {
      sendToOne("OK\n",sc);
      if(message.charAt(0) == '/') {
        message = message.substring(1);
      }
      sendToOther("PRIVATE " + cl.nick + " " + message + "\n",nick);
    }
    else 
      sendToOne("ERROR\n",sc);
  }


  static private Client getClient(SelectionKey key) throws IOException{
    String nick="";
    int state=0;
    String room="";

    Object cl = key.attachment();
    
    Class c = cl.getClass();
    for (Field f : c.getDeclaredFields()) {
      f.setAccessible(true);
      try {
        if(f.getName().equals("nick")) {
          nick = f.get(cl).toString();
       } else if(f.getName().equals("state")) {
          state = Integer.parseInt(f.get(cl).toString());
       } else
          room = f.get(cl).toString();
     } catch (Exception e) {
          e.printStackTrace();
      }
    }

    Client client = new Client(nick,state,room);
    return client;
  }


  static private void sendToOne(String message, SocketChannel sc) throws IOException {

    try {
      ByteBuffer buf = ByteBuffer.allocate(4096);

      buf.clear();
      buf.put(message.getBytes());

      buf.flip();


      while(buf.hasRemaining()) {
        sc.write(buf);
      }
    } catch(IOException e) { System.out.println( e ); }
  }

  static private void sendToOthers(String message, Client cl1) throws IOException{

    ByteBuffer msgBuf=ByteBuffer.wrap(message.getBytes());
    for(SelectionKey key : selector.keys()) {
			if(key.isValid() && key.channel() instanceof SocketChannel) {
        Client cl2 = getClient(key);
        if(cl2.room.equals(cl1.room) && !cl2.nick.equals(cl1.nick)) { //Message to Others    
          SocketChannel sch=(SocketChannel) key.channel();
          sch.write(msgBuf);
          msgBuf.rewind();
        }
			}
		}
  }

  static private void sendToOther(String message, String nick) throws IOException{
    
    ByteBuffer msgBuf=ByteBuffer.wrap(message.getBytes());
    for(SelectionKey key : selector.keys()) {
			if(key.isValid() && key.channel() instanceof SocketChannel) {
        Client cl = getClient(key);
        if(cl.nick.equals(nick)) { //Private Message to Other    
          SocketChannel sch=(SocketChannel) key.channel();
          sch.write(msgBuf);
          msgBuf.rewind();
          break;
        }
      }
    }
  }
}


class Client {
  int state;
  String nick;
  String room;

  Client(String nick, int state, String room) {
    this.nick  = nick;
    this.state = state;
    this.room  = room; 
  }
}
