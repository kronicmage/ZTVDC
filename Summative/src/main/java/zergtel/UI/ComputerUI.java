package zergtel.UI;

import com.github.axet.wget.info.ex.DownloadInterruptedError;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import zergtel.core.Main;
import zergtel.core.converter.Converter;
import zergtel.core.converter.Merge;
import zergtel.core.downloader.Downloader;
import zergtel.core.downloader.EzHttp;
import zergtel.core.io.FileChooser;
import zergtel.core.searcher.Searcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

/**
 * Created by Shyam on 2016-10-25.
 * This class is our implementation of a GUI wrapper around the library functions of zergtel.core
 * When forking this project, this class is completely replaceable with your own GUI implementation
 *
 * If you choose to keep this GUI implementation, there are a number of nuances you should know:
 *   - Cancelling threads proved out to be a very hacky process for us - a number of relevant variables had to be made public
 *     and the calling of the ComputerUI class (see zergtel.core.Main) had to be attached to a static variable in order for the
 *     worker classes to be able to access certain variables of the ComputerUI object. More info on this throughout this class and
 *     in zergtel.core.Main
 *   - Somewhat related to this, there exist two seperate DownloadThread classes with *almost* identical code -
 *     one for each download button in the gui. The primary reason for this is cancelling ambiguity; back when we had one
 *     class for the both, cancelling from one of the buttons would cancel both threads, and vice versa. Also, when buttons
 *     would ungrey after a task being finished, the single download thread would ungrey both the download buttons.
 *     Should you find a better implementation for cancellation that doesn't require static shenanigans, this should be an
 *     easy fix too - all you would need to do is create two instances of the single class rather than two seperate classes
 *     for the download buttons.
 *   - ZTVDC does not, at the moment, support more than one of a particular task at a time (with the exception of downloading,
 *     where technically two can happen at a time). Should one be able to create a more generalizeable worker thread implementation,
 *     that should be easily fixable (along with many of the points listed above).
 *   - Despite the amount of search queries returned being a property in youtube.properties under java/resources, every variable
 *     that has something to with searcher in the UI is implemented with a magic number of 5. You'll have to change some or all of
 *     those if you want to implement support for multiple pages of searches.
 *   - Searcher is non-threaded, which means that there is quite a noticeable delay between searching for something and
 *     seeing the results on the screen. This is primarily due to the need to download thumbnails. Future maintainers should
 *     add browserlike functionality to the loading process to ensure that users can see results despite thumbnails having
 *     not loaded.
 */
public class ComputerUI extends JFrame implements ActionListener{
    /**
     * A number of the variables below could easily be represented as booleans -
     * historically we had intended for more states for a number of these, but in the end those additional features
     * got cut leaving us with the integer booleans we have now.
     */
    private int openingDisplay = 1; //checks if the current panel is the opening panel, if 1 = true, if 0 = false
    private int searchDisplay = 0; //checks if the current panel is the search panel, if 1 = true, if 0 = false
    private int browserDisplay = 0; //checks if the current panel is the browser panel, if 1 = true, if 0 = false
    private int buttonNo = -1; //stores the current number of the button for search selection
    private int numPressed = 0; //stores the number of times the button is pressed, used for browser in order to not constantly reinitialize the same components
    private int swap = 0; //checks if current test label (placeholder) is replaced with image label, 0 = false, 1 = true

    /**
     * The following variables are more int booleans that check whether or not a certain worker thread has been cancelled or not
     * As previously mentioned, cancellation of tasks in progress is implemented in a fairly hacky fashion. These variables exist
     * so that the worker thread may check if it has got an exception due to cancellation, or whether or it's from any other source.
     * You may ask - why not just kill the thread and be done with it?
     * There's a few reasons, namely file cleanup and the relevant gui popup, but we all know that these can be fairly easily fixed
     * if we put the relevant methods outside of the worker classes, but the primary reason we haven't done so is to avoid
     * the display of the "Task complete!" popup on cancellation.
     *
     * In order to fix that, one would have to fix the way we are handling exceptions - more on that later on in this class,
     * and in the relevant zergtel.core classes.
     *
     */
    public int isConverterCancelled = 0; //simple boolean check if converter has been cancelled
    public int isMergeCancelled = 0; //ditto for merge
    public int isDownloadSelectedCancelled = 0; //ditto for download selected thread
    public int isDownloadLinkCancelled = 0; //ditto for download link thread

    //These variables store the thumbnail url and the video url of search results, respectively
    private String[] imageUrl = new String[5];
    private String[] urlStorage = new String[5];

    //These are variables which could honestly be anonymous calls or local variables 99% of the time, but we made static
    //for ease of name recognition
    private String userInput, directory, name, url;
    private File file1, file2;

    //This is the size of the program in pixels
    private Dimension minSize = new Dimension(1080, 635);

    //Various jpanels that make up our GUI
    private JPanel commands = new JPanel();
    private JPanel download = new JPanel();
    private JPanel convert = new JPanel();
    private JPanel search = new JPanel();
    private JPanel openingPanel = new JPanel();
    private JPanel searchPanel = new JPanel();
    private JPanel searchQuery[] = new JPanel[5];
    private JPanel searchFiller[] = new JPanel[5];

    private GroupLayout layout; //Overall layout of the program
    private GroupLayout[] searchList = new GroupLayout[5]; //Layout for search results

    //Arraylist of maps to hold searchresults
    private ArrayList<Map<String, String>> searchResults;

    //Things required to implement a webview for previewing
    private WebView youtube;
    private WebEngine youtubeEngine;
    private JFXPanel browserPanel = new JFXPanel();

    //Assorted Jbuttons, labels, and text areas for the ui
    public JButton downloadSelected = new JButton("Download Selected");
    public JButton downloadLink = new JButton("Download from URL");
    public JButton downloadSelectedCancel = new JButton("Cancel");
    public JButton downloadLinkCancel = new JButton("Cancel");
    public JButton converter = new JButton("      Convert Files      ");
    public JButton converterCancel = new JButton("Cancel");
    public JButton merge = new JButton("Merge");
    public JButton mergeCancel = new JButton("Cancel");
    private JButton preview[] = new JButton[5]; //This one holds all the jbuttons for searching
    private JButton searchKW = new JButton("Search by Key Words");
    private JButton previewURL = new JButton("Preview Selected");
    private JTextArea openingText = new JTextArea();
    private JLabel image[] = new JLabel[5];
    private JLabel title[] = new JLabel[5];
    private JLabel channel[] = new JLabel[5];
    private JLabel description[] = new JLabel[5];
    private JLabel datePublished[] = new JLabel[5];
    private JLabel test[] = new JLabel[5];

    //Initiates a file chooser
    private FileChooser chooser = new FileChooser();

    //Initiates converter and merge objects, and worker threads
    private Converter c = new Converter();
    private Merge m = new Merge();
    private DownloadSelectedWorker downloadSelectedWorker;
    private DownloadLinkWorker downloadLinkWorker;
    private ConvertWorker convertWorker;
    private MergeWorker mergeWorker;

    /**
     * This is the constructor
     */
    public ComputerUI() {
        URL iconURL = getClass().getResource("/zergtel.png");
        ImageIcon icon = new ImageIcon(iconURL);
        this.setIconImage(icon.getImage());

        //Setup the GUI
        setTitle("ZergTel VDC");
        setResizable(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setBackground(new Color(44, 42, 43));
        setMinimumSize(minSize);
        chooser.setDirectory(new File("."));
        this.addComponentListener(new ComponentAdapter(){
            public void componentResized(ComponentEvent e){
                Dimension size = ComputerUI.this.getSize();
                if(size.width<minSize.width)
                    size.width = minSize.width;
                if(size.height<minSize.height)
                    size.height = minSize.height;
                ComputerUI.this.setSize(size);
            }
        });
        setVisible(true);

        //Declare and initialize layouts
        layout = new GroupLayout(getContentPane());
        GroupLayout commandLayout = new GroupLayout(commands);
        GroupLayout downloadLayout = new GroupLayout(download);
        GroupLayout convertLayout = new GroupLayout(convert);
        GroupLayout searchLayout = new GroupLayout(search);
        GroupLayout openingLayout = new GroupLayout(openingPanel);
        GridLayout searcherLayout = new GridLayout(5, 1);

        //set JFrame and panels to a layout
        setLayout(layout);
        commands.setLayout(commandLayout);
        download.setLayout(downloadLayout);
        convert.setLayout(convertLayout);
        search.setLayout(searchLayout); //I made seperate layouts in order to make them resizeable
        openingPanel.setLayout(openingLayout);
        searchPanel.setLayout(searcherLayout);

        //setup for opening panel's text
        openingText.setText("Welcome to ZTVDC!\nUse the Menu to the left for options.");
        openingText.setBackground(null);
        openingText.setFont(new Font("Times New Roman", Font.PLAIN, 32));
        openingText.setEditable(false);

        //add components to the components
        download.add(downloadSelected);
        download.add(downloadSelectedCancel);
        download.add(downloadLink);
        download.add(downloadLinkCancel);
        convert.add(converter);
        convert.add(converterCancel);
        convert.add(merge);
        convert.add(mergeCancel);
        search.add(searchKW);
        search.add(previewURL);
        openingPanel.add(openingText);

        //add border for the components
        openingPanel.setBorder(BorderFactory.createTitledBorder("Welcome to ZTVDC"));
        browserPanel.setBorder(BorderFactory.createTitledBorder("Browser"));
        commands.setBorder(BorderFactory.createTitledBorder("Menu"));
        download.setBorder(BorderFactory.createTitledBorder("Downloader"));
        convert.setBorder(BorderFactory.createTitledBorder("Converter"));
        search.setBorder(BorderFactory.createTitledBorder("Search"));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Results"));

        //Add and setup layout and components for search panel
        for(int i = 0; i < 5; i++) {
            searchQuery[i] = new JPanel();
            searchQuery[i].setBorder(BorderFactory.createTitledBorder("Result #" + (i+1)));
            searchList[i] = new GroupLayout(searchQuery[i]);
            searchQuery[i].setLayout(searchList[i]);
            preview[i] = new JButton("Select");
            searchQuery[i].add(preview[i]);
            title[i] = new JLabel();
            searchQuery[i].add(title[i]);
            channel[i] = new JLabel();
            searchQuery[i].add(channel[i]);
            description[i] = new JLabel();
            searchQuery[i].add(description[i]);
            datePublished[i] = new JLabel();
            searchQuery[i].add(datePublished[i]);
            test[i] = new JLabel();
            image[i] = new JLabel();
            searchQuery[i].add(test[i]);
            searchFiller[i] = new JPanel();
            searchQuery[i].add(searchFiller[i]);


            searchList[i].setHorizontalGroup(searchList[i].createSequentialGroup()
            .addGroup(searchList[i].createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(test[i]))
            .addGroup(searchList[i].createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(title[i], 450, 450, 750))
            .addGroup(searchList[i].createParallelGroup(GroupLayout.Alignment.LEADING))
                    .addComponent(datePublished[i], 75, GroupLayout.DEFAULT_SIZE, 75)
            .addComponent(preview[i], GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
            searchList[i].setVerticalGroup(searchList[i].createSequentialGroup()
            .addGroup(searchList[i].createParallelGroup(GroupLayout.Alignment.CENTER)
                .addComponent(test[i])
                .addComponent(title[i], GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(datePublished[i], GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(preview[i], GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
            searchList[i].setAutoCreateGaps(true);
            searchPanel.add(searchQuery[i]);
        }

        //Organize the layouts used in the components, sets sizes, etc.
        layout.setHorizontalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
                        .addComponent(commands, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addComponent(openingPanel, 0, 700, Short.MAX_VALUE));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup()
                        .addComponent(commands, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(openingPanel, 0, 500, Short.MAX_VALUE)));

        commandLayout.setHorizontalGroup(commandLayout.createSequentialGroup()
                .addGroup(commandLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(download, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(convert, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(search))); //Adding the parameter 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE allows it to force resize, or else it will stay at min size.
        commandLayout.setVerticalGroup(commandLayout.createSequentialGroup()
        .addGroup(commandLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        .addComponent(download, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        .addGroup(commandLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        .addComponent(convert, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        .addComponent(search));

        downloadLayout.setHorizontalGroup(downloadLayout.createSequentialGroup()
        .addGroup(downloadLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
        .addComponent(downloadSelected, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(downloadLink, 0 ,GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        .addGroup(downloadLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
        .addComponent(downloadSelectedCancel, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(downloadLinkCancel, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        downloadLayout.setVerticalGroup(downloadLayout.createSequentialGroup()
        .addGroup(downloadLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
        .addComponent(downloadSelected, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(downloadSelectedCancel, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        .addGroup(downloadLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
        .addComponent(downloadLink, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(downloadLinkCancel, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        convertLayout.setHorizontalGroup(convertLayout.createSequentialGroup()
        .addGroup(convertLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
        .addComponent(converter, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(merge, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        .addGroup(convertLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
        .addComponent(converterCancel, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(mergeCancel, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        convertLayout.setVerticalGroup(convertLayout.createSequentialGroup()
        .addGroup(convertLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
        .addComponent(converter, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(converterCancel, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        .addGroup(convertLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
        .addComponent(merge, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(mergeCancel, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        searchLayout.setHorizontalGroup(searchLayout.createSequentialGroup()
        .addGroup(searchLayout.createParallelGroup()
        .addComponent(searchKW, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(previewURL, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        searchLayout.setVerticalGroup(searchLayout.createSequentialGroup()
        .addGroup(searchLayout.createParallelGroup()
        .addComponent(searchKW, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        .addGroup(searchLayout.createParallelGroup()
        .addComponent(previewURL, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        openingLayout.setHorizontalGroup(openingLayout.createSequentialGroup()
        .addComponent(openingText, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        openingLayout.setVerticalGroup(openingLayout.createSequentialGroup()
        .addComponent(openingText, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));

        //add action listeners for the buttons
        downloadSelected.addActionListener(this);
        downloadSelectedCancel.addActionListener(this);
        downloadLink.addActionListener(this);
        downloadLinkCancel.addActionListener(this);
        converter.addActionListener(this);
        converterCancel.addActionListener(this);
        merge.addActionListener(this);
        mergeCancel.addActionListener(this);
        searchKW.addActionListener(this);
        previewURL.addActionListener(this);
        for(int i = 0; i < 5; i++)
            preview[i].addActionListener(this);

        //set buttons to false where the user is required to do a certain action in the GUI before being able to access these buttons
        downloadSelected.setEnabled(false);
        downloadSelectedCancel.setEnabled(false);
        downloadLinkCancel.setEnabled(false);
        converterCancel.setEnabled(false);
        mergeCancel.setEnabled(false);
        previewURL.setEnabled(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        //downloads selected search result
        if (e.getSource() == downloadSelected) {
            directory = chooser.choose("Choose where to save the downloaded file", JFileChooser.DIRECTORIES_ONLY).getAbsolutePath() + "\\";
            if (!directory.equals(null)) {
                EzHttp.setDownloadLocation(directory);
                url = urlStorage[buttonNo];
                downloadSelectedWorker = new DownloadSelectedWorker(url);
                JOptionPane.showMessageDialog(null, "Downloading has begun - we'll alert you when it's done.");
                downloadSelectedWorker.execute();
                downloadSelectedCancel.setEnabled(true);
                downloadSelected.setEnabled(false);
            }
        }
        //cancels download for selected search result
        if (e.getSource() == downloadSelectedCancel) {
            isDownloadSelectedCancelled = 1;
            downloadSelectedWorker.cancel(true);
            downloadSelectedCancel.setEnabled(false);
            downloadSelected.setEnabled(true);
            new File(directory + name).delete();
            isDownloadSelectedCancelled = 0;
        }
        //downloads video from url
        if (e.getSource() == downloadLink) {
            url = JOptionPane.showInputDialog(null, "Insert BandCamp, YouTube, or raw file link to download from");
            System.out.println("Url: " + url);
            if (!url.equals(null)) {
                directory = chooser.choose("Choose where to save the downloaded file", JFileChooser.DIRECTORIES_ONLY).getAbsolutePath() + "\\";

                if (!directory.equals(null)) {
                    EzHttp.setDownloadLocation(directory);
                    downloadLinkWorker = new DownloadLinkWorker(url);
                    JOptionPane.showMessageDialog(null, "Downloading has begun - we'll alert you when it's done.");
                    downloadLinkWorker.execute();
                    downloadLinkCancel.setEnabled(true);
                    downloadLink.setEnabled(false);
                }
            }
        }
        //cancels download from url
        if (e.getSource() == downloadLinkCancel) {
            isDownloadLinkCancelled = 1;
            downloadLinkWorker.cancel(true);
            downloadLinkCancel.setEnabled(false);
            downloadLink.setEnabled(true);
            new File(directory + name).delete();
            isDownloadLinkCancelled = 0;
        }
        //converts a selected file into a new file, name chosen by the user
        if (e.getSource() == converter) {
            try {
                file1 = chooser.choose("Select file to convert", JFileChooser.FILES_ONLY);
                if (!file1.equals(null)) {
                    directory = chooser.choose("Choose where to save the output file", JFileChooser.DIRECTORIES_ONLY).getAbsolutePath();
                    if (!directory.equals(null)) {
                        name = JOptionPane.showInputDialog(null, "Insert name of the new file (Include format) example: test.mp4");
                        convertWorker = new ConvertWorker(file1, directory, name);
                        JOptionPane.showMessageDialog(null, "Conversion has begun - we'll alert you when it's done");
                        convertWorker.execute();
                    }
                }
            } catch (HeadlessException e1) {
                e1.printStackTrace();
                JOptionPane.showMessageDialog(null, e1.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
            converterCancel.setEnabled(true);
            converter.setEnabled(false);
        }
        //cancels conversion
        if (e.getSource() == converterCancel) {
            isConverterCancelled = 1;
            convertWorker.cancel(true);
            converterCancel.setEnabled(false);
        }
        //merges 2 files selected files from the user into a new file (name chosen by user)
        if (e.getSource() == merge) {
            file1 = chooser.choose("Select video source file", JFileChooser.FILES_ONLY);
            if (!file1.equals(null)) {
                file2 = chooser.choose("Select audio source file", JFileChooser.FILES_ONLY);
                if (!file2.equals(null)) {
                    directory = chooser.choose("Choose where to save the output file", JFileChooser.DIRECTORIES_ONLY).getAbsolutePath();
                    if (!directory.equals(null)) {
                        name = JOptionPane.showInputDialog(null, "Insert name of the new file (Include format) example: test.mp4");
                        mergeWorker = new MergeWorker(file1, file2, directory, name);
                        JOptionPane.showMessageDialog(null, "Merging has begun - we'll alert you when it's done.");
                        mergeWorker.execute();
                    }
                }
            }
            merge.setEnabled(false);
            mergeCancel.setEnabled(true);
        }
        //cancels merge
        if (e.getSource() == mergeCancel) {
            isMergeCancelled = 1;
            mergeWorker.cancel(true);
            mergeCancel.setEnabled(false);
        }
        //searches results from youtube search query and displays important content to user
        if (e.getSource() == searchKW) {
            userInput = JOptionPane.showInputDialog(null, "Please enter your search query");
            if (!userInput.equals("")) {
                searchResults = Searcher.search(userInput);
                URL[] url = new URL[5];
                ImageIcon[] imageStored = new ImageIcon[5];
                BufferedImage[] images = new BufferedImage[5];
                for (int i = 0; i < searchResults.size(); i++) { //magic number, beware
                    Map<String, String> result = searchResults.get(i);
                    title[i].setText("<html>" + "Title: " + result.get("title") + "<br>" + "Channel: " + result.get("channel") + "<br>" + "<font color = 'gray'>" + "Description: " + result.get("description") + "</font>" + "</html>");
                    datePublished[i].setText(result.get("datePublished"));
                    urlStorage[i] = result.get("url");
                    imageUrl[i] = result.get("thumbnail");
                    try {
                        url[i] = new URL(imageUrl[i]);
                        images[i] = ImageIO.read(url[i]);
                        imageStored[i] = new ImageIcon(images[i]);
                        image[i].setIcon(imageStored[i]);
                    } catch(MalformedURLException e3) {
                        e3.printStackTrace();
                    } catch(IOException e4) {
                        e4.printStackTrace();
                    }
                    if (swap == 0) {
                        searchList[i].replace(test[i], image[i]);
                        searchList[i].linkSize(SwingConstants.VERTICAL, image[i], preview[i]);
                    }
                    searchList[i].replace(image[i], image[i]);
                }
                if (searchResults.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "No search results found.", "Woops!", 0);
                } else {
                    swap++;
                    if (openingDisplay == 1 && searchDisplay == 0) {
                        layout.replace(openingPanel, searchPanel);
                        openingDisplay = 0;
                        searchDisplay = 1;
                    } else if (browserDisplay == 1 && searchDisplay == 0) {
                        layout.replace(browserPanel, searchPanel);
                        browserDisplay = 0;
                        searchDisplay = 1;
                    }
                }
            }
        }
        //previews the url from user's selected search
        if(e.getSource() == previewURL) {
            switch (buttonNo) {
                case 0: url = urlStorage[0];
                    browser();
                    break;
                case 1: url = urlStorage[1];
                    browser();
                    break;
                case 2: url = urlStorage[2];
                    browser();
                    break;
                case 3: url = urlStorage[3];
                    browser();
                    break;
                case 4: url = urlStorage[4];
                    browser();
                    break;
            }
        }
        for(int i = 0; i < 5; i++)
        {
            if(e.getSource() == preview[i])
            {
                buttonNo = i;
                JOptionPane.showMessageDialog(null, "You may now use the appropriate button on the left to preview or download.", "Video selected!", 1);
                downloadSelected.setEnabled(true);
                previewURL.setEnabled(true);
            }
        }

    }

    /**
     * Method that calls our preview feature (playback feature)
     * Webview is very finicky - try not to adjust too much unless you know what you're doing (which we still really don't)
     */
    public void browser() {
        Platform.setImplicitExit(false);
        Platform.runLater(() -> {
            if (numPressed != 0)
                youtubeEngine.getLoadWorker().cancel();
            if(numPressed == 0) {
                youtube = new WebView();
                youtubeEngine = youtube.getEngine();
                browserPanel.setScene(new Scene(youtube));
            }
            youtubeEngine.load(url);
        });
        if (browserDisplay == 0 && openingDisplay == 1) {
            layout.replace(openingPanel, browserPanel);
            browserDisplay = 1;
            openingDisplay = 0;
            youtubeEngine.getLoadWorker().cancel();
        } else if (browserDisplay == 0 && searchDisplay == 1) {
            layout.replace(searchPanel, browserPanel);
            browserDisplay = 1;
            searchDisplay = 0;
        }
        numPressed++;
    }
}

/**
 * The following four classes are our assorted threads for downloading, merging, and converting
 * As previously stated, these are coded in a fairly spaghetti fashion in order to make button un/regreying possible
 * after a successful completion of task, and to make cancelling work.
 *
 * One issue that might help fix the latter situation if solved is the exception handling - in the Downloader, Converter,
 * and merge classes in zergtel.core, methods throw a generic Exception - should we patch the program to include better exception
 * classes and handling, that should allow for us to remove the need for the isXCancelled variables. Still wouldn't help
 * for button re/degreying though.
 *
 * The ultimate dream for these classes is a generalized worker class that could potentially take in inputs in its constructor
 * relevant to what command will be run (to bad java doesn't have first class functions), and what buttons will be affected.
 */

class DownloadSelectedWorker extends SwingWorker<String, Void> {
    String url;

    DownloadSelectedWorker(String downUrl) {
        url = downUrl;
    }

    @Override
    public String doInBackground() {
        String output = "";
        try {
            output = Downloader.get(url);
            JOptionPane.showMessageDialog(null, "Downloading has finished for " + output);
        } catch (DownloadInterruptedError ex){
            JOptionPane.showMessageDialog(null, "Downloading has been cancelled!");
        } catch (Exception ex) {
            ex.printStackTrace();
            if (Main.ui.isDownloadSelectedCancelled != 1) {
                JOptionPane.showMessageDialog(null, "Downloading has stopped!", "Oh no!", JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(null, "Downloading has been cancelled!");
            }
            return ex.getMessage();
        }
        Main.ui.downloadSelectedCancel.setEnabled(false);
        Main.ui.downloadSelected.setEnabled(true);
        return output;
    }
}

class DownloadLinkWorker extends SwingWorker<String, Void> {
    String url;

    DownloadLinkWorker(String downUrl) {
        url = downUrl;
    }

    @Override
    public String doInBackground() {
        String output = "";
        try {
            output = Downloader.get(url);
            JOptionPane.showMessageDialog(null, "Downloading has finished for " + output);
        } catch (DownloadInterruptedError ex){
            JOptionPane.showMessageDialog(null, "Downloading has been cancelled!");
        } catch (Exception ex) {
            ex.printStackTrace();
            if (Main.ui.isDownloadLinkCancelled != 1) {
                JOptionPane.showMessageDialog(null, "Downloading has stopped!", "Oh no!", JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(null, "Downloading has been cancelled!");
            }
            return ex.getMessage();
        }
        Main.ui.downloadLinkCancel.setEnabled(false);
        Main.ui.downloadLink.setEnabled(true);
        return output;
    }
}

class ConvertWorker extends SwingWorker<String, Void> {
    String directory, name;
    File inFile;
    private Converter converter = new Converter();

    ConvertWorker(File input, String dir, String nom) {
        inFile = input;
        directory = dir;
        name = nom;
    }

    @Override
    public String doInBackground() {
        try {
            converter.convert(inFile, directory, name);
            if (converter.getTerminated() == 0 && Main.ui.isConverterCancelled == 0)
                JOptionPane.showMessageDialog(null, "Conversion has finished for " + name);
            else if(converter.getTerminated() == 0 && Main.ui.isConverterCancelled == 1) {
                JOptionPane.showMessageDialog(null, "Conversion was cancelled for " + name);
                converter.app.destroy();
                Main.ui.isConverterCancelled = 0;
            }
        } catch (DownloadInterruptedError ex){
            JOptionPane.showMessageDialog(null, "Conversion was cancelled for" + name);
        } catch (Exception ex) {
            ex.printStackTrace();
            if (Main.ui.isConverterCancelled != 1) {
                JOptionPane.showMessageDialog(null, "Conversion has stopped!", "Oh no!", JOptionPane.ERROR_MESSAGE);
            }
            Main.ui.isConverterCancelled = 0;
        }
        Main.ui.converterCancel.setEnabled(false);
        Main.ui.converter.setEnabled(true);
        return null;
    }
}

class MergeWorker extends SwingWorker<String, Void> {
    String directory, name;
    File file1, file2;
    private Merge merger = new Merge();

    MergeWorker(File f1, File f2, String dir, String nom) {
        file1 = f1;
        file2 = f2;
        directory = dir;
        name = nom;
    }

    @Override
    public String doInBackground() {
        try {
            merger.merge(file1, file2, directory, name);
            if(merger.getTerminated() == 0 && Main.ui.isMergeCancelled == 0)
                JOptionPane.showMessageDialog(null, "Merging has finished for " + name);
            else if(merger.getTerminated() == 0 && Main.ui.isMergeCancelled == 1) {
                JOptionPane.showMessageDialog(null, "Merging was cancelled for " + name);
                merger.app.destroy();
                Main.ui.isMergeCancelled = 0;
            }
        } catch (DownloadInterruptedError ex){
            JOptionPane.showMessageDialog(null, "Merging was cancelled for " + name);
        } catch (Exception ex) {
            ex.printStackTrace();
            if (Main.ui.isMergeCancelled != 1) {
                JOptionPane.showMessageDialog(null, "Oh no! Something goofed!", "Error", JOptionPane.ERROR_MESSAGE);
            }
            Main.ui.isMergeCancelled = 0;
        }
        Main.ui.mergeCancel.setEnabled(false);
        Main.ui.merge.setEnabled(true);
        return null;
    }
}


