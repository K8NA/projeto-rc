import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.nio.charset.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas com a interface gráfica

    //Socket
    BufferedReader inFromServer;
    DataOutputStream outToServer;
    Socket clientSocket;





    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }


    // Construtor
    public ChatClient(String server, int port) throws IOException {


        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
      outToServer =
       new DataOutputStream(clientSocket.getOutputStream());
      inFromServer =
       new BufferedReader(new
             InputStreamReader(clientSocket.getInputStream()));


      if(isCommand(message)) {
        outToServer.writeBytes(message + '\n');
      } else if(message.charAt(0) == '/') {
        outToServer.writeBytes("/" + message + "\n");
      } else
        outToServer.writeBytes(message + "\n");

    }

    public boolean isCommand(String message) {
        String[] commands = message.split(" ");
        String command = commands[0];

        return command.equals("/nick")  ||
               command.equals("/join")  ||
               command.equals("/bye")   ||
               command.equals("/leave") ||
               command.equals("/priv");
    }


    // Método principal do objecto
    public void run() throws IOException {

        outToServer =
         new DataOutputStream(clientSocket.getOutputStream());
        inFromServer =
         new BufferedReader(new
               InputStreamReader(clientSocket.getInputStream()));

         try {
           while(true) {
            String messageFromServer = inFromServer.readLine();
            if(messageFromServer!=null) {
                 printMessage(messageFromServer + "\n");
            }
            if(messageFromServer.equals("BYE")) {
                clientSocket.close();
                break;
            }
          }
         }
         catch(IOException e) { clientSocket.close(); }
    }


    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.clientSocket = new Socket(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
