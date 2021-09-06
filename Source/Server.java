import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.*;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;

public class Client {
    
    /* --- Socket components --- */
    final static int PORT = 8080;   //Port number where our sockets will connect
    Socket socket;                  //Client socket 
    BufferedReader in;              //Buffered input for socket
    PrintWriter out;                //output for socket
    String username;
    Boolean isConnected,isRunning;            //to keep track of socket connection status

    /* --- Directory Related --- */
    DirectoryManager directoryManager;  //handles all directory related operations (used in both server and client)
    String diskLetter;                  //diskletter A/B/C/... for local directories of client

    /* --- GUI Components --- */
    //this are needed by other functions hence they are placed outside of GUI function
    JFrame frame; //Frame to hold other components
    JTextPane outputView;

    public Client(){
        isConnected = false;    //set connection status to false initially
        frame = new JFrame();
        
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BorderLayout());
        JTextField inputText = new JTextField(""); //textfields for input via GUI      
        inputText.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_ENTER){
                    String command = inputText.getText();      //saving command entered by user
                    inputText.setText("");              //reseting input field
                    send(command);                      //sending command over to server
                }
            }
        });
        JLabel usernameLabel = new JLabel();
        inputPanel.add(usernameLabel,BorderLayout.WEST);
        inputPanel.add(inputText,BorderLayout.CENTER);

        JButton connectButton;
        connectButton = new JButton("Connect");
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                try{
                    connectToServer();
                }catch(IOException e){
                    print(e.toString()+"\n");
                }
            }
        });

        JButton usernameButton = new JButton("Create Username");
        usernameButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                username = JOptionPane.showInputDialog(frame, "Enter Username");
                if(isUsernameValid(username)){    //checking if username is valid    
                    usernameButton.setText("Change Username");  //updating text of username button
                    usernameLabel.setText(username);
                }
                else{
                    username = "";
                }
            }
        });

        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                isRunning = false;
                if(isConnected)
                    send("quit");   //server closes the socket when it recives "quit" message
                isConnected = false;                                
            }
        });
    
        outputView = new JTextPane();                    // TextPane which will act as a console for gui to desplays results and errors
		outputView.setEditable(false);                             // it will be read-only for user
        JScrollPane scrollPane = new JScrollPane(outputView);      //to make console scrollable

	    JPanel buttonsPanel = new JPanel();
		buttonsPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
		buttonsPanel.setLayout(new GridLayout(2,3));
		
		buttonsPanel.add(connectButton);
		buttonsPanel.add(usernameButton);
		buttonsPanel.add(exitButton);
        
        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(buttonsPanel, BorderLayout.SOUTH);
        
        frame.setSize(640,480);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setTitle("Client");
		frame.setVisible(true);

        isRunning   = true;
    }

    void print(String s){
        outputView.setText(outputView.getText()+s);
    }
    void connectToServer() throws IOException{
        if (isConnected){                                       //skip if already connected
            print("Already connected\n");
        }
        else if (isUsernameValid(username)){                    //double check if username is valid
            InetAddress address = InetAddress.getLocalHost();   //local machine address, change this to make server work on public address
            socket = new Socket(address,PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));    //using buffered reader is better performance wise
            out = new PrintWriter(socket.getOutputStream());    
            out.println(username);                              //sending username over to server to check if its available
            out.flush();                                        //need to flush the outstream to send the message immediatly
            diskLetter = in.readLine();                         //recive diskletter A/B/C/.. from server
            if(diskLetter.equals("")){
                print("Error : username taken. please try different username\n"); 
                diskLetter = null;
            }
            else{
                isConnected = true;                             //assume client connected if non empty diskletter recived
                print("Connected to server\n");
                frame.setTitle("Client "+diskLetter);
                directoryManager = new DirectoryManager("ClientsDir",diskLetter);
                send("sync");                                       //intiating synchronization process
            }
        }
    }
    Boolean isUsernameValid(String username){
        if (username == null){
            print("Enter a username for connection\n");
            return false;
        }
        else if (!username.matches("^[a-zA-Z0-9]+$")){          //regex to match alphanumeric username
            print("Username is empty or not valid! Alphanumeric only\n");
            return false;
        }
        return true;
    }
    void send(String message){
        if(isConnected){        //send and print the message if connected
            print(String.format("» %s\n",message));
            out.println(message);
            out.flush();
        }
        else{
            print("Not connected to server\n");
        }
    }
    void close(){                //socket.close() can throw IOExecption
        frame.setTitle("Client");
        File home = new File(directoryManager.root,diskLetter);
        try{
            if(home.exists())
                directoryManager.deleteDirectory(home); //remove local sync directory A/B/C/...
            socket.close();
        }
        catch(IOException e){
            System.out.println(e);
        }
        print("Disconnected from server\n");
        isConnected=false;                          //make connection status false initially
    }

    String process(String response){
        //tokenize response from server into ArrayList using " " as seprator
        String[] command = response.split(" ");
        String message = "";
        //command[0] holds opertation code and subsequent cells hold operands
        try{
        switch(command[0]){
            case "sync_mkdir":
                directoryManager.createDirectory(command[1]);
                break;
            case "sync_rm":
                directoryManager.deleteDirectory(command[1]);
                break;
            case "sync_cd":
                directoryManager.changeDirectory(command[1]);
                break;
            case "quit":
                close();
                message = "closing connection";
                break;
            default:
                message = response;
        }
        }
        catch(IOException e){
            System.out.println(e);
        }
        return message;
    }

    public void exit() {
        if(diskLetter!=null){
            File home = new File(directoryManager.root,diskLetter);
            if(home.exists())
                try{        
                    directoryManager.deleteDirectory(home); //remove local sync directory A/B/C/...
                }
                catch(IOException e){
                    System.out.println(e);
                }
        }
        frame.dispose();
        System.exit(0);
    }

    public void run() {
        while(isRunning){
            try{
                Thread.sleep(500);  //checking too fast can cause program to misbehave
            }
            catch(InterruptedException e){
                System.out.println("While wating got Interrupted by "+e);
            }
            if(isConnected){
                try{                                    //try-catch inside while because otherwise it will break while loop
                    if(in.ready()){
                        String response = in.readLine();    //skip if not connected
                        response=process(response);                      
                        if(!response.equals(""))            //print to console if not empty
                            print(response+"\n");
                    }

                }
                catch(IOException e){
                    print(e.toString()+"\n");
                    isConnected = false;
                }
            }
        }
        exit();
    }
    public static void main(String[] args){
        Client c = new Client();    
        c.run();
    }
}
