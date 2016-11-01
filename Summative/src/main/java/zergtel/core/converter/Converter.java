package zergtel.core.converter;

import sun.security.util.SecurityConstants;

import java.io.*;


public class Converter 
{
    Process app;
    public void convert(File f, String n, String fn) //f1 = format
    {
        try {
            String outFile = n;
            String  format = fn;
            Runtime convert = Runtime.getRuntime();
            app = convert.exec("ffmpeg -i " + f.getName() + " " + outFile + "." + format);
            BufferedReader appReader = new BufferedReader(new InputStreamReader(app.getInputStream()));

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void cancel() { app.destroy(); }
    public void merge(File f1, File f2, String n, String fn)
    {
        try {
            String outFile = n;
            String  format = fn;
            Runtime convert = Runtime.getRuntime();
            app = convert.exec("ffmpeg -i " + f1.getName() + "-i " + f2.getName() + "-c:v copy -c:a aac -strict experimental " + n + "." + fn);
            BufferedReader appReader = new BufferedReader(new InputStreamReader(app.getInputStream()));

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //http://stackoverflow.com/questions/17123118/how-to-stop-ffmpeg-that-runs-through-java-process
    //http://stackoverflow.com/questions/10927718/how-to-read-ffmpeg-response-from-java-and-use-it-to-create-a-progress-bar
	//https://docs.oracle.com/javase/tutorial/sound/converters.html
	//JAVE Library http://www.sauronsoftware.it/projects/jave/index.php
	//Stuff to experiment with: Joba Time, Java Media Framework API (JMF)
	//https://github.com/Rebant/Video-Converter/blob/master/videoConverter.java
	//https://www.ffmpeg.org/
    //https://github.com/wseemann/FFmpegMediaMetadataRetriever
}
