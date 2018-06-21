package Server;

import Common.Command.Command;
import Common.Command.ConnectMe;
import Common.Command.EndElection;
import Common.Election;
import Common.IdNamePair;
import Common.Command.StartElection;
import Common.Crypto.Crypto;
import Common.MinerInfo;
import Server.ConnectionHandler.ConnectionHandlerThread.*;
import Server.ConnectionHandler.ServerConnectionHandler;
import Server.Dispatcher.DispatcherMonitor;
import Server.Dispatcher.DispatcherThread;
import Server.Registrar.Authenticator;
import Server.Registrar.Registrar;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.lang3.tuple.MutablePair;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import sun.misc.Signal;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyPair;
import java.util.*;

import static Common.GUI.getGridPane;

public class ServerMain extends Application {
    public static final Object mon = new Object();
    public static final ObservableList<String> _miners = FXCollections.observableArrayList();

    private Stage stage;

    public static void main(String[] args) {
        initServer();
        launch(args);
    }

    @Override
    public void start(Stage s) throws Exception {
        stage = s;
        stage.setTitle("Server");
        stage.show();
        initEmailScreen();
    }

    @Override
    public void stop() throws Exception {
        Signal.raise(new Signal("INT"));
        super.stop();
    }

    public void initEmailScreen() {
        GridPane g = getGridPane();

        Text addrTxt = new Text("E-mail:"),  pwdTxt = new Text("Password:");
        TextField addrTxtfield = new TextField();
        PasswordField pwdField = new PasswordField();
        Button loginButton = new Button("Login");
        Button continueWOEmailButton = new Button("Continue without email");
        HBox hbox = new HBox(5, loginButton, continueWOEmailButton);
        Text ack = new Text();
        String defaultText = "default (smtp.domain)";
        TextField SMTPTxtField = new TextField(defaultText);

        g.add(addrTxt, 0, 0);
        g.add(addrTxtfield, 1, 0);
        g.add(pwdTxt, 0, 1);
        g.add(pwdField, 1, 1);
        g.add(new Text("STMP Server:"), 0, 2);
        g.add(SMTPTxtField, 1, 2);
        g.add(hbox, 1, 3);
        g.add(ack, 1, 4);

        continueWOEmailButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> serverScreen());
        loginButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            String addr = addrTxtfield.getText().trim(),
                    pwd = pwdField.getText(),
                    smtp = SMTPTxtField.getText().trim();
            try {
                if (addr.isEmpty() || pwd.isEmpty() || smtp.isEmpty())
                    ack.setText("Error: E-mail, password or SMTP server is empty");
                else if (!ServerEmailSender.isValid(addr)) {
                    ack.setText("Error: Invalid e-mail address");
                } else {
                    if (smtp.equals(defaultText))
                        smtp = addr.split("@")[1];
                    ServerEmailSender.init(addr, pwd, smtp);
                    serverScreen();
                }
            } catch (Exception e) {
                ack.setText("Error: " + e.getCause().getMessage());
                System.err.println(e.getCause().getMessage());
            }
        });

        stage.setScene(new Scene(g));
    }

    public void serverScreen() {
        int row = 0;
        GridPane g = getGridPane();

        Button xmlSelectButton = new Button("Select from file system"),
                usersSelectButton = new Button("Select from file system"),
                seButton = new Button("Start election");
        TextArea ack = new TextArea();
        ack.setEditable(false);
        ack.setWrapText(true);

        PrintStream ps = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                Platform.runLater( () -> ack.appendText(String.valueOf((char) b)));
            }
        });

        System.setOut(ps);

        TextField xmlTxtField = new TextField("election.xml"),
                usersTxtField = new TextField("users.txt");

        HBox xmlBox = new HBox(5, xmlTxtField, xmlSelectButton),
                usersBox = new HBox(5, usersTxtField, usersSelectButton);

        Button initEmailButton = new Button("Change e-mail account");

        TextField period = new TextField(Integer.toString(DispatcherThread.getControlPeriod() / (int)1e3));
        Button setPeriod = new Button("Set");

        Text periodText = new Text(Integer.toString(DispatcherThread.getControlPeriod() / (int)1e3));
        HBox periodBox = new HBox(5, new Text("Block generation period: "), periodText, new Text(" sec."), period, setPeriod);
        periodBox.setAlignment(Pos.CENTER_LEFT);

        setPeriod.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            try {
                int p = Integer.parseInt(period.getText().trim()) * (int) 1e3;
                DispatcherThread.setControlPeriod(p);
                periodText.setText(period.getText().trim());
            } catch (Exception e) {
            }
        });


        Button eeButton = new Button("End Election");
        Text ongoingElectionsTxt = new Text("Ongoing elections:");
        MutablePair<Integer, String>[] ongoingElections = Registrar.getOngoingElections();

        //list View for educational qualification
        ObservableList<String> names = FXCollections.observableArrayList();
        for (MutablePair<Integer, String> e : ongoingElections)
            names.add("(" + e.left + ", " + e.right + ")");
        ListView<String> electionsListView = new ListView<>(names);

        List<MinerInfo> infos = DispatcherMonitor.getMiners();
        ObservableList<String> miners = FXCollections.observableArrayList();
        Text connectedMinersTxt = new Text("Connected miners:");

        for (MinerInfo info : infos)
            miners.add(info.toString());

        ListView<String> minersListView = new ListView<>(miners);

        String tmp = ServerEmailSender.getAccount();
        tmp = tmp == null ? "none" : tmp;
        HBox emailbox = new HBox(5, new Text("Server e-mail: " + tmp), initEmailButton);
        emailbox.setAlignment(Pos.CENTER_LEFT);

        g.add(emailbox, 0, row++);
        g.add(periodBox, 0, row++);
        row++;
        g.add(new Text("Election XML and user list:"), 0, row++);
        g.add(xmlBox, 0, row++);
        g.add(usersBox, 0, row++);
        g.add(seButton, 0, row++);
        g.add(new HBox(5,
                new VBox(5, ongoingElectionsTxt, electionsListView),
                new VBox(5, connectedMinersTxt, minersListView)
        ), 0, row++);
        g.add(eeButton, 0, row++);
        g.add(ack, 0, row++);

        initEmailButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> initEmailScreen());

        seButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (DispatcherMonitor.getNumMiners() == 0) {
                System.out.println("Couldn't start election: No miner connected");
            } else {
                String xml = xmlTxtField.getText().trim(),
                        users = usersTxtField.getText().trim();
                if (xml.isEmpty() || users.isEmpty()) {
                    System.out.println("Election XML or user list is empty");
                    return;
                }

                MutablePair<MutablePair<KeyPair, Election>, MutablePair<Map<String, String>, List<Email>>>  retval = null;

                try {
                    retval = parseElectionXML(xml, users);
                } catch (IOException e) {
                    String err = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    System.out.println("Error: " + err);
                } catch (ParserConfigurationException | SAXException e) {
                    System.out.println("Error: " + e.getMessage());
                }

                if (retval != null) {
                    Registrar.startElection(retval);
                    Command c = new StartElection(retval.getKey().getValue());
                    c.setSignature(Crypto.sign(ServerKeyKeeper.getKeys().getPrivate(), c.getRawData()));
                    ServerConnectionHandler.broadcast(c);
                    System.out.println("Election started\n" + retval.left.right);
                    names.add("(" + retval.left.right.getId() + ", " + retval.left.right.getName() + ")");
                    electionsListView.refresh();
                }
            }
        });

        xmlSelectButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            try {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Election XML chooser");
                File file = fileChooser.showOpenDialog(stage);
                xmlTxtField.setText(file.getPath());
            } catch (NullPointerException e) {}
        });

        usersSelectButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            try {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("User list chooser");
                File file = fileChooser.showOpenDialog(stage);
                usersTxtField.setText(file.getPath());
            } catch (NullPointerException e) {}
        });

        eeButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (DispatcherMonitor.getNumMiners() == 0) {
                System.out.println("Couldn't end election: No miner connected");
                return;
            }
            try {
                Command c = new EndElection();
                String s = electionsListView.getSelectionModel().getSelectedItem();
                c.setElectionID(Integer.parseInt(s.substring(1, s.indexOf(','))));
                ((EndElection) c).setKey(ServerKeyKeeper.getBlindSignatureKeys(c.getElectionID()).getPrivate().getEncoded());
                c.setSignature(Crypto.sign(ServerKeyKeeper.getKeys().getPrivate(), c.getRawData()));
                System.out.println("Generating last blocks");
                DispatcherMonitor.dispatchSeq();
                Registrar.endElection(c.getElectionID());
                ServerConnectionHandler.broadcast(c);
                System.out.println(("Done"));
                names.remove(s);
                electionsListView.refresh();
            } catch (IndexOutOfBoundsException e) {
            }
        });

        _miners.addListener(new ListChangeListener<String>() {
            @Override
            public void onChanged(Change<? extends String> c) {
                Platform.runLater(()->{
                    miners.setAll(c.getList());
                    minersListView.refresh();
                });
            }
        });

        stage.setScene(new Scene(g));
    }

    private static void initServer() {
        ServerKeyKeeper.init();
        ServerConnectionHandler.init();
        ServerDBHandler.init();

        List<MutablePair<MutablePair<KeyPair, Election>, Authenticator>> elections = ServerDBHandler.getElections();
        Registrar.restore(elections);

        if (!Registrar.getElectionIDs().isEmpty())
            ServerConnectionHandler.restoreQ();

        Thread minerThread = new Thread(new MinerConnectionThread());
        minerThread.start();

        synchronized (mon) {
            try {
                mon.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        List<InetAddress> miners = ServerDBHandler.getMiners();
        ServerDBHandler.removeMiners();
        {
            Command c = new ConnectMe();
            c.setSignature(Crypto.sign(ServerKeyKeeper.getKeys().getPrivate(), c.getRawData()));

            for (InetAddress addr : miners) {
                try {
                    Socket s = new Socket(addr, 4002);
                    ObjectInputStream in = new ObjectInputStream(s.getInputStream());
                    ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                    out.writeObject(c);
                    s.close();
                } catch (IOException e) {
//                    e.printStackTrace();
                }
            }
        }

        Thread clientThread = new Thread(new ClientConnectionThread());
        Thread dispatcher = new Thread(new DispatcherThread());
        clientThread.start();
        dispatcher.start();
    }

    private static MutablePair<MutablePair<KeyPair, Election>, MutablePair<Map<String, String>, List<Email>>>
    parseElectionXML(String xml, String userFile) throws IOException, ParserConfigurationException, SAXException {
        Election e = null;
        KeyPair pair = null;
        Map<String, String> users = null;
        List<Email> mails = null;

        { //election
            File inputFile = new File(xml);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            NodeList name = doc.getElementsByTagName("name"),
                    nvote = doc.getElementsByTagName("nvote"),
                    candidates = doc.getElementsByTagName("candidate");

            e = new Election();
            e.setId(Registrar.getNumElections());
            e.setName(name.item(0).getTextContent().trim());
            e.setCandidates(new ArrayList<>());

            for (int j = 0; j < candidates.getLength(); ++j)
                e.getCandidates().add(new IdNamePair(j, candidates.item(j).getTextContent().trim()));

            pair = Crypto.generateKeyPair();
            e.setBlindSigKey(pair.getPublic());

            String s = nvote.item(0).getTextContent().trim();
            try {
                int tmp = Integer.parseInt(s);
                e.setNvotes_l(tmp);
                e.setNvotes_h(tmp);
            } catch (NumberFormatException e1) {
                String[] tmp = s.split("-");
                e.setNvotes_l(Integer.parseInt(tmp[0].trim()));
                e.setNvotes_h(Integer.parseInt(tmp[1].trim()));
            }
        }
        { //users
            BufferedReader br = new BufferedReader(new FileReader(userFile));
            users = new HashMap<>();
            mails = new ArrayList<>();
            String s;
            while ((s = br.readLine()) != null) {
                if (s.trim().isEmpty())
                    continue;
                if (ServerEmailSender.isValid(s.substring(1, s.length() - 1))) {
                    String[] tmp = s.substring(1, s.length() - 1).split("@");
                    mails.add(new Email(tmp[0], tmp[1]));
                } else {
                    String[] crs = s.split(":");
                    users.put(crs[0].trim().substring(1, crs[0].length() - 1),
                            crs[1].trim().substring(1, crs[1].length() - 1));
                }
            }
        }

        return new MutablePair<>(new MutablePair<>(pair, e), new MutablePair<>(users, mails));
    }
}