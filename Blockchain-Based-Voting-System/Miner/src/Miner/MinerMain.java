package Miner;

import Common.*;
import Common.Block.BlockFrame;
import Common.Command.StartElection;
import Common.TLS.SSLUtil;
import Miner.BlockChain.BlockChainHandler;
import Miner.CommandHandler.AuditCommandHandler;
import Miner.CommandHandler.CommandHandler;
import Common.Command.Command;
import Common.Transaction.Transaction;
import Miner.ConnectionHandler.MinerConnectionHandler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import sun.misc.Signal;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static Common.GUI.getGridPane;

public class MinerMain extends Application {
    private static MinerType type;
    private static ObjectInputStream in;
    private static ObjectOutputStream out;
    private static CommandHandler commandHandler;
    private static BlockChainHandler b;
    private static final Object mon = new Object();
    private static SSLContext sc = null;
    private static SSLSocketFactory sslSocketFactory = null;
    private Stage stage;

    public static void main(String[] args) {
        MinerDBHandler.init();
        MinerConnectionHandler.init();
        type = MinerType.MINER;

        if (args.length == 0) { //gui
            launch(args);
        } else {    //cli
            try {
                MinerKeyKeeper.readKeyPair("pub.key", "priv.key");
            } catch (IOException e) {
                MinerKeyKeeper.generateKeyPair();
            }
            try {
                sc = SSLUtil.createClientSSLContext();
            } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException | KeyManagementException e) {
                e.printStackTrace();
            }
            sslSocketFactory = sc.getSocketFactory();
            initMiner(null, args[0], 4001);
        }
    }

    @Override
    public void start(Stage s) throws Exception {
        stage = s;
        stage.setTitle("Miner");
        stage.show();
        minerStartScreen();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        Signal.raise(new Signal("INT"));
    }

    public void minerStartScreen() {
        Text text1 = new Text("Server: ");
        Text ack = new Text("");

        TextField hostField = new TextField("localhost");
        Button connectButton = new Button("Connect");

        ToggleGroup tg = new ToggleGroup();
        RadioButton mnr = new RadioButton("Miner");
        mnr.setToggleGroup(tg);
        RadioButton audit = new RadioButton("Audit");
        audit.setToggleGroup(tg);
        HBox box = new HBox(20, mnr, audit);
        tg.selectToggle(type.equals(MinerType.MINER) ? mnr : audit);


        Button generateKeyPairButton = new Button("Generate key pair");

        TextField pubKeyTextField = new TextField("public.key");
        Button open1 = new Button("Select from file system");

        TextField prvKeyTextField = new TextField("private.key");
        Button open2 = new Button("Select from file system");

        //Creating a Grid Pane
        GridPane g = getGridPane();

        int row = 0;

        if (type.equals(MinerType.MINER)) {
            g.add(pubKeyTextField, 0, row);
            g.add(open1, 1, row++);
            g.add(prvKeyTextField, 0, row);
            g.add(open2, 1, row++);
            g.add(generateKeyPairButton, 0, row++);
        }
        row++;
        g.add(text1, 0, row++);
        g.add(hostField, 0, row++);
        g.add(box, 0, row++);
        g.add(connectButton, 0, row++);
        g.add(ack, 0, row++);

        mnr.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (!type.equals(MinerType.MINER)) {
                type = MinerType.MINER;
                minerStartScreen();
            }
        });

        audit.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (!type.equals(MinerType.AUDIT)) {
                type = MinerType.AUDIT;
                minerStartScreen();
            }
        });

        generateKeyPairButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            String pubPath = pubKeyTextField.getText().trim(),
                    prvPath = prvKeyTextField.getText().trim();

            if (pubPath.isEmpty() || prvPath.isEmpty()) {
                ack.setText("Error: Public key path or private key path is empty");
            } else {
                MinerKeyKeeper.generateKeyPair();
                try {
                    Common.writeTofile(MinerKeyKeeper.getKeyPair().getPublic().getEncoded(), pubPath);
                    Common.writeTofile(MinerKeyKeeper.getKeyPair().getPrivate().getEncoded(), prvPath);
                    ack.setText("Done");
                } catch (IOException e) {
                    ack.setText("Fail");
                    e.printStackTrace();
                }
            }

        });

        open1.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            try {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Public key chooser");
                File file = fileChooser.showOpenDialog(stage);
                pubKeyTextField.setText(file.getPath());
            } catch (NullPointerException e) {}

        });

        open2.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            try {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Private key chooser");
                File file = fileChooser.showOpenDialog(stage);
                prvKeyTextField.setText(file.getPath());
            } catch (NullPointerException e) {}
        });

        connectButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (type.equals(MinerType.MINER)) {
                try {
                    MinerKeyKeeper.readKeyPair(pubKeyTextField.getText().trim(), prvKeyTextField.getText().trim());
                } catch (IOException e) {
                    ack.setText("Error: Couldn't read key pair");
                    return;
                }
            }

            String host = hostField.getText().trim();

            if (host.isEmpty()) {
                ack.setText("Error: Host field is empty!");
                return;
            }

            try {
                final int port = 4001;
                sc = SSLUtil.createClientSSLContext();
                sslSocketFactory = sc.getSocketFactory();
                SSLSocket s = (SSLSocket) sslSocketFactory.createSocket(host, port);
                s.startHandshake();
                connectedScreen(s, host, port);
            } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException | KeyManagementException e) {
                ack.setText("Error: " + e.getMessage());
            }
        });

        stage.setScene(new Scene(g));
    }

    public void connectedScreen(SSLSocket s, String host, int port) {
        final TextArea txt = new TextArea();
        txt.setEditable(false);
        txt.setWrapText(true);
        PrintStream printStream = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                Platform.runLater( () -> txt.appendText(String.valueOf((char) b)) );            }
        });
        System.setOut(printStream);

        stage.setScene(new Scene(txt, 600, 750));
        new Thread(()->initMiner(s, host, port)).start();
    }


    public static void initMiner(SSLSocket s, String host, int port) {

        boolean init = true, connected = false;

        for (;;) {
            try {
                if (s == null) {
                    s = (SSLSocket) sslSocketFactory.createSocket(host, port);
                    s.startHandshake();
                }
                out = new ObjectOutputStream(s.getOutputStream());
                in = new ObjectInputStream(s.getInputStream());
                connected = true;
                MinerConnectionHandler.setPeer(s, out);
                MinerKeyKeeper.setServerPublicKey((PublicKey) in.readObject());

                Object o = in.readObject();
                if (init) {
                    Queue<Object> q = new LinkedList<>();

                    if (o.equals(true)) {
                        Set<Integer> electionIDs = (Set<Integer>) in.readObject();
                        InetAddress[] peers = (InetAddress[]) in.readObject();
                        SSLSocket finalS = s;
                        new Thread(() ->
                        {
                            b = new BlockChainHandler();
                            commandHandler = new CommandHandler(b);

                            List<StartElection> l = MinerDBHandler.getStartElectionCmds();
                            for (StartElection se : l) {
                                if (!electionIDs.contains(se.getElectionID())) {
                                    MinerDBHandler.deleteElection(se.getElectionID());
                                    l.remove(se);
                                } else {
                                    commandHandler.startElection(se);
                                    MinerDBHandler.importBlocks(se.getElectionID(), b.getBlockchain(se.getElectionID()));
                                }
                            }

                            P2P.sync(peers, finalS.getInetAddress(), commandHandler, electionIDs);

                            MinerConnectionHandler.sendObject(Acknowledge.SUCCESS);
                        }).start();

                        while (true) {
                            Object tmp = in.readObject();
                            if (tmp.equals(Acknowledge.SUCCESS)) //blocks are received
                                break;
                            ((LinkedList<Object>) q).push(tmp);
                        }
                    } else {
                        b = new BlockChainHandler();
                        commandHandler = type.equals(MinerType.MINER) ? new CommandHandler(b) : new AuditCommandHandler(b);
                    }

                    new Thread(() -> P2P.handleRequests(commandHandler, mon)).start();

                    while (!q.isEmpty())
                        handler(((LinkedList<Object>) q).pop());
                }

                Queue<Object> q = new ConcurrentLinkedQueue<>();

                new Thread(()->{
                    for (;;) {
                        while (!q.isEmpty()) {
                            try {
                                handler(q.poll());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        synchronized (q) {
                            try {
                                if (q.isEmpty())
                                    q.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();

                for (;;) {
                    o = in.readObject();
                    q.add(o);
                    synchronized (q) {
                        q.notify();
                    }
                }
            } catch (IOException e) {
                if (!connected) {
                    System.out.println("Couldn't connected to server: " + e.getMessage());
                    System.exit(0);
                } else {
                    try {
                        s = null;
                        synchronized (mon) {
                            System.out.println("Server disconnected. Waiting...");
                            mon.wait();
                            host = P2P.getHost();
                            init = false;
                        }
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handler(Object o) {
        System.out.println(o);
        synchronized (b) {
            try {
                if (o instanceof Command) {
                    System.out.println(o);
                    commandHandler.handleCommand((Command) o);
                } else if (o instanceof Transaction) {
                    b.addTx((Transaction) o);
                } else if (o instanceof BlockFrame) {
                    b.addBlock((BlockFrame) o);
                } else if (o instanceof MinerInfo) {
                    initMinerInfo(o);
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    private static void initMinerInfo(Object o) {
        System.out.println("I'm miner " + ((MinerInfo) o).getId());

        if (type.equals(MinerType.AUDIT)) {
            ((MinerInfo) o).setType(MinerType.AUDIT);

        } else if (type.equals(MinerType.MINER)) {
            ((MinerInfo) o).setType(MinerType.MINER);
            ((MinerInfo) o).setPublicKey(MinerKeyKeeper.getKeyPair().getPublic());
        }

        MinerConnectionHandler.sendObject((Serializable) o);
        commandHandler.setMinerInfo((MinerInfo) o);
    }
}