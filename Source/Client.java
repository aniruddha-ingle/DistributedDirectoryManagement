import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.*;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;

public class Server {

    /* --- Socket components --- */
    final static int PORT = 8080;       //Port number where our sockets will connect
    ServerSocket serverSocket;
    Boolean isConnected;                //to keep track of socket server socker connection status
    List<String> connectedUsernames;    //List of already connected usernames
    List<Character> diskLetters;        //List of already alloted diskletters A/B/C/..

    /* --- GUI Components --- */
    JFrame frame;           //Frame to hold other components
    JTextPane outputView;        //to display user commands and corresponding results
    JTabbedPane logsPane;
    JTextPane userStatus;

    List<ServerThread> serverThreads;

    public Server() {
        isConnected = false;    //make connection status false initially
        
        //Synchronized lists for thread safe access
        connectedUsernames = Collections.synchronizedList(new ArrayList<String>());
        diskLetters = Collections.synchronizedList(new ArrayList<Character>());
        
        //List of child server threads
        serverThreads = new ArrayList<ServerThread>();

        frame = new JFrame();  
        
        JButton connectButton = new JButton("Connect");
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                try{
                    if(isConnected){    //Skip if already connected
                        print("Already connected\n");
                    }
                    else {
                        serverSocket = new ServerSocket(PORT);
                        print(String.format("Server started on port %d\n",PORT));
                        isConnected = true;
                    }
                }catch(IOException e){
                    print(e.toString()+"\n");
                }
            }
        });
        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                for(ServerThread serverThread : serverThreads){
                    if(serverThread.isAlive())
                        serverThread.exit();
                }
                frame.dispose();    //Closing GUI
                System.exit(0);     //Exiting program with no errors
            }
        });
        
        JPanel buttonsPanel = new JPanel();
		buttonsPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
		buttonsPanel.setLayout(new GridLayout(0,2));
		
		buttonsPanel.add(connectButton);
        buttonsPanel.add(exitButton);

        JTabbedPane tabbedPane = new JTabbedPane();

        outputView = new JTextPane();  // TextPane which will act as a console for gui to desplays users commands and errors
        outputView.setEditable(false); //we do not want to type on the console just display output 
        tabbedPane.addTab("Live",new JScrollPane(outputView));

        logsPane = new JTabbedPane();
        tabbedPane.addTab("Logs",new JScrollPane(logsPane));

        userStatus = new JTextPane();
        frame.add(userStatus, BorderLayout.NORTH);
        frame.add(tabbedPane, BorderLayout.CENTER); //the console, in the center
		frame.add(buttonsPanel, BorderLayout.SOUTH); //the buttons are in the panel1 that is placed to the north
		
		frame.setSize(640,480);
		frame.setLocationRelativeTo(null);
		frame.setResizable(false); //fixes the size of the window
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setTitle("Server");
		frame.setVisible(true);
    }
    void print(String s){
        outputView.setText(outputView.getText()+s);
    }
    public void run() { 
        while(true) {   // Runs until Exit button is pressed
            try{
                Thread.sleep(500);  //checking too fast can cause program to misbehave
            }
            catch(InterruptedException e){
                System.out.println("While wating got Interrupted by "+e);
            }
            
            if(isConnected){
                try{
                    Socket clientSocket = serverSocket.accept();
                    //starting a new thread to handle new connection
                    ServerThread serverThread = new ServerThread(connectedUsernames,diskLetters,clientSocket,outputView,logsPane,userStatus);
                    if(serverThread.clientConnected){ 	//check if connection was successful
                        serverThread.start();			
                        serverThreads.add(serverThread); 	//stored in list to close the serverThread
                    }
                }
                catch(IOException e){
                    print(e.toString()+"\n"); //prints exception to console
                }
            }
        }
        
    }

    public static void main(String[] args) {
        Server s = new Server();
        s.run();    //starting server loop
    }
}

class Log {
    String command; //handles text commands for adding and removing from log
    Timestamp timestamp; //used for log display format and checking temporal dependency
    
    Log(String _command){ 
        command = _command;
        timestamp = new Timestamp(System.currentTimeMillis()); //assigns current time-stamp 
    }
    @Override
    public String toString() {
        return "["+timestamp+"]: "+command; //output formating for time - stamp 
    }
    public boolean isDependentOn(Log logB){ //checks if this Log A depends on Log B
        if(timestamp.compareTo(logB.timestamp) < 0){ //if compareTo returns negative integer then set dependency to false 
            return false; 							 //because commands occurring before cannot be causally dependent on the commands occuring after them
        }
        
        String[] c1 = logB.command.split(" "); // used to split command into operation and operands
        String[] c2 = command.split(" "); 
        //we are checking if c2 depends on c1
        switch(c1[0]){ // operation name in first command is used for switch case
            case "mkdir": 
                switch(c2[0]){ //operation name in second command is used for switch case
                    case "ls": 
                        if(c2.length==1)   
                            return false; //ls without operand cannot have dependency
                    case "mkdir": //fall through to rm
                    case "rm":
                            return c1[1].matches("^"+c2[1]+".*");  //checks if path of command 2 and command 1 have matching parent directories
                    case "mv":
                        return c1[1].matches("^"+c2[1]+".*") || c1[1].matches("^"+c2[2]+".*"); //checks matching parent directory for both operands because operation is move
                    case "rn":
                        return c1[1].equals(c2[1]); //checking if current name and new name are same
                    default:
                        return false;
                }    
            //the above logic is repeated for other file operations wherever applicable in the cases below
            case "rm":
                switch(c2[0]){
                    case "mkdir":
                        return c1[1].matches("^"+c2[1]+".*");
                    case "mv":
                        return c1[1].equals(c2[1]);
                    case "rn":
                        return c1[1].equals(c2[1]);
                    case "rm":
                    case "ls":
                    default:
                        return false;
                }    
            case "mv":
                switch(c2[0]){
                    case "mkdir":
                        return c1[1].equals(c2[1]);
                    case "rm":
                        return c1[2].matches("^"+c2[1]+".*");
                    case "mv":
                        return c1[2].matches("^"+c2[1]+".*") || c1[1].matches("^"+c2[2]+".*");
                    case "rn":
                        return c1[2].matches("^"+c2[1]+".*") || c1[1].equals(c2[1]);
                    case "ls":
                        if(c2.length==1)
                            return false;
                        return c1[1].matches("^"+c2[2]+".*");
                    default:
                        return false;
                }
            case "rn":
                switch(c2[0]){
                    case "mkdir":
                        return c1[1].equals(c2[1]) || c1[2].matches("^"+c2[1]+".*");
                    case "rm":
                        return c1[2].matches("^"+c2[1]+".*");
                    case "mv":
                        return c1[2].matches("^"+c2[1]+".*") || c1[1].equals(c2[2]);
                    case "rn":
                        return c1[2].matches("^"+c2[1]+".*") || c1[1].equals(c2[1]);
                    case "ls":
                        if(c2.length==1)
                            return false;
                        return c1[1].matches("^"+c2[2]+".*");
                    default:
                        return false;
                }    
            case "ls":  //no commands are dependent on ls
                return false;
            default:
                return false;
        }
    }
}
class Logger {
    String username; //used to store and handle the username of client
    List<Log> logs; //used to store all the logs
    public Stack<String> undoCommands; //used to store the commands to be undone
    JTabbedPane logsPane;
    JTextPane logPane;

    public Logger(String _username, JTabbedPane _logsPane){ //Initializing above created variables
        username = _username;
        logs = new ArrayList<Log>(); 
        undoCommands = new Stack<String>();
        logsPane = _logsPane;
        logPane = new JTextPane();
        logPane.setEditable(false);
        logsPane.addTab(username,logPane);
    }
    public void exit(){
        logsPane.remove(logPane);
    }
    public void add(String command){ //adds new log to the List<Log> called logs
        Log newLog = new Log(command);
        logs.add(newLog);
    }
    public void addUndo(String commandString){ //adds complementary commands to the undo stack
        String[] command = commandString.split(" ");
        switch(command[0]){
            case "mkdir":
                undoCommands.push(String.format("rm %s", command[1])); //mkdir can be undone by rm
                break;
            case "rm":
                undoCommands.push(String.format("mkdir %s", command[1])); //rm can be undone by mkdir
                break;
            case "mv":
                undoCommands.push(String.format("mv %s %s", command[2], command[1])); //mv a to b is undone by mv b to a
                break;
            case "rn":
                undoCommands.push(String.format("rn %s %s", command[2], command[1])); //rn a to b is undone by rn b to a
                break;
            case "ls":
                break; //ls does not warrant an undo operation
            default:
        }
    }
    public void delete(int index){ //recursively, deletes the chosen log and all the logs that are causally dependent on the chosen log
        Log delLog = logs.remove(index);  	//delLog pops log from the list of logs
        addUndo(delLog.command);			//adds complementary command of the command to be undone
        
        for(int i=index; i<logs.size(); i++){ 	//recursively deletes logs if dependent
            Log log = logs.get(i);
            if(log.isDependentOn(delLog)){
                delete(i);
            }
        }
    }
    public String printLogs(){ 	//formats and returns string to print on console
        String  log = "Logs:-\n";
        for(int i=0; i<logs.size(); i++){
            log += String.format("%3d. %s %s\n",i,username,logs.get(i));
        }
        return log;
    }
    public void updateLogPane(){
        logPane.setText(printLogs());
    }
}

class ServerThread extends Thread {
    Socket clientSocket; 
    BufferedReader in;
    PrintWriter out;
    Map<String,WatchKey> watchers;  //to watch for changes in directories filesystem
    DirectoryManager directoryManager;
    Boolean clientConnected;
    List<String> usernames;
    String username;
    List<Character> diskLetters;
    Character diskLetter;   //if declared char it will be treated as an Integer index by List<Character>
    JTextPane outputView,userStatus;
    JTabbedPane logsPane;
    Logger logger;

    ServerThread(List<String> usernames, List<Character> diskLetters, Socket clientSocket,JTextPane outputView,JTabbedPane logsPane,JTextPane userStatus) throws IOException{
        this.clientSocket = clientSocket;
        this.outputView = outputView;
        this.logsPane = logsPane;
        this.userStatus = userStatus;
        this.usernames = usernames;
        this.diskLetters = diskLetters;

        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        out = new PrintWriter(clientSocket.getOutputStream());
        
        username = in.readLine();
        if(usernames.contains(username)){
            send("");        //sending empty diskletter
            clientSocket.close();
            clientConnected = false;
        }
        else{
            usernames.add(username);    //add username to list of usernames already in use
            print(username+" connected\n");
            updateUsernames();
            diskLetter = 'A';
            while(diskLetter!='Z'){
                if(!diskLetters.contains(diskLetter))
                    break;
                diskLetter++;
            }
            diskLetters.add(diskLetter);
            
            send(diskLetter.toString());
            
            logger = new Logger(username,logsPane);
            directoryManager = new DirectoryManager("ServerDir",username);
            watchers = new HashMap<String,WatchKey>();  //to access watcherkey objects by name(string) of the directories they are watching
            clientConnected = true;
        }
    }
    void print(String s){
        outputView.setText(outputView.getText()+s);
    }

    void exit(){										//acting as a destructor only difference is that the calls are manual
        print(username+" disconnected\n");
        logger.exit();
        usernames.remove(username);
        updateUsernames();
        diskLetters.remove(diskLetter);
        send("quit");
    }
    void send(String message){ 							//to send message to client
        out.println(message);
        out.flush();
    }
    
    void updateUsernames(){ 							//used to print realtime connected list of usernames
        String usernameList = "Connected Users: ";
        for(String username : usernames){
            usernameList += username+" ";
        }
        userStatus.setText(usernameList);
    }
    
    void sendDirectory(String name){ 					//wrapper for string input			
        File dir = new File(directoryManager.root,name);
        sendDirectory(dir);
    }
    void sendDirectory(File dir){ 						//used to send entire directory when syncing, recursively
        if(dir.isDirectory()){
            send("sync_mkdir "+dir.getName());
            send("sync_cd "+dir.getName());
            for(File file : dir.listFiles()){
                sendDirectory(file);
            }
            send("sync_cd ..");
        }
    }
    String process(String message,Boolean isUndo){     	//this function processes the commands recieved from client
        //tokenizing the command
        String[] command = message.split(" ");
        
        //to remove the effect of working directory on undo of commands
        File currDir = directoryManager.PWD;
        if(isUndo){
            directoryManager.PWD = new File(directoryManager.root, username);
        }
        
        String response=""; //stores the response to be sent to the client
        try{
        switch(command[0]){ //switch case for the operation name
        	//each case first handles the improper format of the command and then performs the appropriate subroutine for the 
        	//execution of the command, furthermore, it sets the appropriate response to be sent to the user via the gui of the client
            case "mkdir":
                if(command.length!=2) {
                    response = "Invalid Format (format : mkdir nameOfDirectory)";
                }
                else {
                    directoryManager.createDirectory(command[1]);
                    if(!isUndo)
                        logger.add("mkdir "+directoryManager.getCurrentDir()+command[1]);
                    response = command[1]+" was created";
                }
                break;
            case "rm":
                if(command.length!=2)
                    response = "Invalid Format (format : rm nameOfDirectoryOrFile)";
                else{
                    directoryManager.deleteDirectory(command[1]);
                    if(!isUndo)
                        logger.add("rm "+directoryManager.getCurrentDir()+command[1]);
                    response = command[1]+" was removed";
                }
                break;
            case "mv":
                if(command.length!=3)
                    response = "Invalid Format (format : mv source target)";
                else{
                    directoryManager.moveDirectory(command[1],command[2]);
                    if(!isUndo)
                        logger.add("mv "+directoryManager.getCurrentDir()+command[1]
                                    +" "+directoryManager.getCurrentDir()+command[2]);
                    response = command[1]+" was moved to "+command[2];
                }
                break;
            case "rn":
                if(command.length!=3)
                    response = "Invalid Format (format : mv source target)";
                else{
                    directoryManager.renameDirectory(command[1],command[2]);
                    if(!isUndo)
                        logger.add("rn "+directoryManager.getCurrentDir()+command[1]
                                    +" "+directoryManager.getCurrentDir()+command[2]);
                    response = command[1]+" was renamed to "+command[2];
                }
                break;
            case "ls":
                if(command.length<1 || command.length>2)
                    response = "Invalid Format (format : ls [nameOfDirectory])";
                else {
                    File lsDir; //directory of which contents will be displayed
                    if(command.length==2){
                        String homeDir = directoryManager.root.getPath()+"/"+username;
                        if(command[1].equals("..") && homeDir.equals(directoryManager.PWD.getPath())) //inhibits the access of parent directory 
                            throw new IOException("Error : Insufficient permissions");
                        lsDir = new File(directoryManager.PWD+"/"+command[1]);
                        if(!lsDir.exists())
                            throw new IOException("Error : "+command[1]+" doesn't exist");
                        logger.add("ls "+directoryManager.getCurrentDir()+command[1]);
                    }
                    else{
                        lsDir = directoryManager.PWD;
                        logger.add("ls");
                    }
                    send("Contents of "+lsDir.getName());
                    for(String file : directoryManager.listContents(lsDir)){
                        send(file);
                    }
                }
                break;
            case "quit":
                response = "quit";
                clientConnected = false;
                break;
            case "sync":
                if(command.length!=1)
                    response = "Invalid Format (format : sync)";
                else {
                	//prompt user for directories to be synced
                    send("Available server directories :-");
                    for(String dir : directoryManager.listContents(directoryManager.root)){
                        send(dir);
                    }
                    send("Enter name of directories to sync (seprated by space) :");
                    //accept names of directories to be synced
                    String reply = in.readLine();
                    if(reply.equals("")){
                        send("Sync Session Over");
                        break;
                    }
                    if(reply.equals("quit")){
                        response = "quit";
                        clientConnected = false;
                        break;
                    }
                    //split the response into individual directory names and iterate
                    for(String dir : reply.split(" ")){
                        File f = new File(directoryManager.root,dir);
                        if(f.exists()){ 									//checks if directory exists
                        	
                            Path p = Paths.get(directoryManager.root.getPath()+"/"+dir);
                            WatchService watcher = p.getFileSystem().newWatchService();    
                            
                            try{
                            	//sets up watcher service to listen for events that are triggered by changes in the directory
                                WatchKey watchKey = p.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,StandardWatchEventKinds.ENTRY_MODIFY);
                                watchers.put(dir, watchKey);
                                send("Synchronizing "+dir+" ...");
                                sendDirectory(dir);
                                send(dir+" synchronized");
                            }
                            catch(Exception e){
                                print(e.toString()+"\n");
                            }
                        }
                        else{
                            send(dir+" dosn't exist");
                        }
                    }
                }
                break;
            case "dsync":
                int i = 1;
                while(i < command.length){
                    if(watchers.remove(command[i])!=null) //removes the set watcher to stop listening for changes
                        send("sync_rm "+command[1]);	  // removes directory to be dsynced from the client directory
                        send(command[i]+" desynchronized");
                    i++;
                }
                break;
            case "log":
                if(command.length!=1)
                    response = "Invalid Format (format : log)";
                else {
                    send(logger.printLogs()); //prints logs to let the user chose
                    send("Enter index of command to be delete : (-1 to cancel)");
                    String reply = in.readLine();
                    if(reply.equals("quit")){
                        response = "quit";
                        clientConnected = false;
                        break;
                    }
                    int index = Integer.parseInt(reply);
                    if(index == -1){		 //allows exit from undo session
                        send("Undo session canceled");
                        break;
                    }
                    send("Undo operation started...");
                    logger.delete(index);   //deletes appropriate logs
                    while(!logger.undoCommands.isEmpty()){
                       process(logger.undoCommands.pop(),true); //performs corresponding undo commands
                    }
                    send("Undo operation complete");
                    send(logger.printLogs()); //prints new logs
                }
                break;
            default:
                response = "ERROR : Unknown command";
        }
        }
        catch(IOException e){
            response = e.toString();
        }
        if(isUndo){
            response = "";
            directoryManager.PWD = currDir;
        }
        if(!response.matches(""))
            print(response+"\n");
        return response; //returns the repsonse needed to be sent 
    }
    public void run() {
        String message = "";
        while(clientConnected){
            try{
                Thread.sleep(500); //checking too fast can cause program to misbehave
            }
            catch(InterruptedException e){
                System.out.println("While wating got Interrupted by "+e);
            }

            try{
                if(in.ready()){
                    message=in.readLine();
                    print(String.format("%s » %s\n",username,message));
                    String response = process(message,false);
                    if(!response.matches("")){
                        send(response);
                    }
                }
                logger.updateLogPane();
                //send updates in synchronized directories
                for(Map.Entry<String,WatchKey> watcher : watchers.entrySet()){
                    // get list of events as they occur
                    List<WatchEvent<?>> events = watcher.getValue().pollEvents();
                    //iterate over events, each event corresponds to a specific set of changes that need to be snced
                    for (WatchEvent<?> event : events) {
                        String update="";
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            update = "sync_mkdir " + watcher.getKey() +"/"+ event.context().toString();
                        }
                        else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            update = "sync_rm " + watcher.getKey() +"/"+event.context().toString();
                        }
                        else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            send("sync_rm " + watcher.getKey());
                            sendDirectory(watcher.getKey());
                            update = "";
                        }
                        send(update);
                    }
                }
            }
            catch(Exception e){ 				//print exceptions and quit connection - expected exceptions are socket related
                print(e.toString()+"\n");
                send(e.toString());
                send("quit");
                clientConnected = false;
            }
        }
        exit(); //manual call for the pseudo-destructor
    }
}
