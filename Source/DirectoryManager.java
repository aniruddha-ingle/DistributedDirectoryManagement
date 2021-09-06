import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DirectoryManager {
    String username;        //username to check for permissions
    public File PWD,root;   //PWD(Present Working Directory) and root directory files
    Pattern pattern;        // to process paths with regex
    static String osPathSeprator = "\\\\";   // "\\\\" for windows and "/" for unix based
    public DirectoryManager(String directory,String _username){
        username = _username;
        root = new File(directory);
        if(!root.exists())              //create root directory if it doesn't exists
            root.mkdir();
        PWD = new File(root,username);  //set home directory(root/username) as PWD
        if (!PWD.exists()) {            //create PWD directory if it doesn't exists
            PWD.mkdir();
        }
        pattern = Pattern.compile(root + osPathSeprator + username + osPathSeprator + "(.*)");
    }

    public Boolean exists(String name){
        File directory = new File(PWD, name);
        return directory.exists();
    }

    public String getCurrentDir(){
        Matcher matcher = pattern.matcher(PWD.getPath());
        if(matcher.matches())
            return matcher.group(1)+"/";
        return "";
    }

    public void changeDirectory(String path) throws IOException{
        String[] dirs = path.split("/");    //split path by / , e.g a/b/c => [a,b,c]
        for(String dir : dirs){
            if(dir.equals("..")){          //case : moving backwards (to parent directory)
                String home = root.getPath()+"/"+username;
                if(home.equals(PWD.getPath())){ //to confine user to their home directories
                    throw new IOException("Error : Insufficient permissions");
                }
                PWD = PWD.getParentFile();
            }
            else{                           //case : moving into a directory
                File newPWD = new File(PWD,dir);
                if(newPWD.exists()){    //set PWD to selected directory it it exists
                    PWD = newPWD;
                }
                else{
                    throw new IOException("Directory "+path+"doesn't exist");
                }
            }
        }
    }
	
	public void createDirectory(String path) throws IOException{
        Boolean didCreate = false;
        String[] dirs = path.split("/");
        File currDir = PWD; //creating a copy of PWD because we don't want to cd and change our PWD
        //keep moving to subdirectories in path and creating if they dont exists
        for(String dir : dirs){
            File directory = new File(currDir, dir);
            if(!directory.exists()){
                directory.mkdir();
                didCreate = true;
            }
            currDir = directory;
        }
        if(didCreate==false){
            throw new IOException(path+" already exists");
        }
	}
	public boolean deleteDirectory(String path) throws IOException{
        String[] dirs = path.split("/");
        File currDir = PWD; //creating a copy of PWD because we don't want to cd and change our PWD
        //keep moving to subdirectories  if they exists
        for(String dir : dirs){
            currDir = new File(currDir,dir);
            if(!currDir.exists())
                throw new IOException("target doesn't exist");
        }
        //finally delete the last directory in path, e.g delete only "d" in "a/b/c/d"
        return deleteDirectory(currDir);
    }
	public boolean deleteDirectory(File directory) throws IOException{
		//https://www.programiz.com/java-programming/examples/delete-directory > Example 3
		if(directory.isDirectory()) {
			for(File file : directory.listFiles()) {
                // recursive call if the sub-directory is non-empty
                deleteDirectory(file);
            }
        }

		return directory.delete();
    }
    
    public void moveDirectory(String source, String target) throws IOException{
        //getting Path for source and target directories
        Path sourceFilePath = Paths.get(PWD.getPath()+"/"+source);
        Path targetFilePath = Paths.get(PWD.getPath()+"/"+target);
        //using builtin move function which uses Paths
        Files.move(sourceFilePath,targetFilePath);
    }

    public void renameDirectory(String current, String target) throws IOException{
        File currentFile = new File(PWD.getPath()+"/"+current);
        File targetFile = new File(PWD.getPath()+"/"+target);
        if(targetFile.exists())
            throw new IOException("target file already exists");
        //using inbuilt rename function
        currentFile.renameTo(targetFile);
    }
	
	public String [] listContents(File directory) throws IOException{
		/*
		 * Returns an array of strings, each element in array is a file name
		 */
		File [] dirContents = directory.listFiles();
		String [] arrOfFileNames = new String[dirContents.length];
		int i = 0;
		for(File file: dirContents) {
			arrOfFileNames[i++] = file.getName();
        }
        return arrOfFileNames;
    }
}
