package Client;

import Common.*;
import Common.Command.Request.GetElectionResult;
import Common.Command.Request.GetVote;
import Common.Crypto.Crypto;
import Common.TLS.SSLUtil;
import Common.Token.Token;
import Common.Transaction.Vote;
import Common.User.User;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.lang3.tuple.MutablePair;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSABlindingEngine;
import org.bouncycastle.crypto.generators.RSABlindingFactorGenerator;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Random;

import static Common.Common.writeTofile;
import static Common.GUI.getGridPane;

public class GUI_v2 extends Application {
    private Stage stage;
    private SSLSocket sslSocket = null;
    private ObjectInputStream in = null;
    private ObjectOutputStream out = null;
    private KeyPair keyPair = null;
    private Token token = null;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage s) {
        stage = s;
        stage.setTitle("Blockchain-Based Voting System");
        startScreen();
        stage.show();
    }

    private synchronized void connectServer(String host) throws CertificateException,
            NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        if (in != null && out != null)
            return;

        final int port = 4000;

        try {
            SSLContext sc = SSLUtil.createClientSSLContext();
            SSLSocketFactory sslSocketFactory = sc.getSocketFactory();
            sslSocket = (SSLSocket) sslSocketFactory.createSocket(host, port);
            sslSocket.startHandshake();

            out = new ObjectOutputStream(sslSocket.getOutputStream());
            in = new ObjectInputStream(sslSocket.getInputStream());
        } catch (Exception e) {
            throw e;
        }
    }

    private void startScreen() {
        Text text1 = new Text("Server: ");
        Text result = new Text("");

        TextField hostField = new TextField("localhost");
        Button connectButton = new Button("Connect");
        Button getMinersButton = new Button("Get Miners");

        ToggleGroup tg = new ToggleGroup();
        RadioButton srv = new RadioButton("Server");
        srv.setToggleGroup(tg);
        RadioButton mnr = new RadioButton("Miner/Audit");
        mnr.setToggleGroup(tg);
        HBox box = new HBox(20, srv, mnr);
        tg.selectToggle(srv);
        //Creating a Grid Pane
        GridPane gridPane = getGridPane();

        //Arranging all the nodes in the grid
        gridPane.add(text1, 0, 0);
        gridPane.add(hostField, 1, 0);
        gridPane.add(box, 1, 2);
        gridPane.add(new HBox(10, connectButton, getMinersButton), 1, 3);
        gridPane.add(result,1,  5);

        EventHandler<MouseEvent> connect = new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                try {
                    if(tg.getSelectedToggle().equals(srv)) {
                        connectServer(hostField.getText().trim());
                        connectedScreen();
                    } else {
                        queryFromMinerScreen(hostField.getText().trim());
                    }
                } catch (CertificateException | NoSuchAlgorithmException
                        | KeyStoreException | KeyManagementException | IOException e) {
                    result.setText("Couldn't connected to the server: " + e.getMessage());
                }
            }
        };

        connectButton.addEventFilter(MouseEvent.MOUSE_CLICKED, connect);
        getMinersButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            try {
                connectServer(hostField.getText().trim());
                out.writeObject(Job.GetMiners);
                List<MinerInfo> miners = (List<MinerInfo>) in.readObject();

                if (miners.isEmpty())
                    result.setText("No miners connected");
                else
                    getMinersScreen(miners);
            } catch (CertificateException | NoSuchAlgorithmException
                    | KeyStoreException | KeyManagementException | IOException e) {
                result.setText("Couldn't connected to the server: " + e.getMessage());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        });

        //Creating a scene object
        stage.setScene(new Scene(gridPane));
    }

    private void getMinersScreen(List<MinerInfo> miners) {
        GridPane gridPane = getGridPane();
        Text text = new Text("Miners:");
        Text result = new Text();
        Button connect = new Button("Connect");
        Button prev = new Button("Prev");

        ObservableList<String> infos = FXCollections.observableArrayList();
        for (MinerInfo info : miners)
            infos.add(info.toString());
        ListView<String> mv = new ListView<>(infos);

        gridPane.add(prev, 0, 0);
        gridPane.add(text, 0, 1);
        gridPane.add(mv, 0, 2);
        gridPane.add(connect, 0, 3);
        gridPane.add(result, 0, 4);

        prev.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> startScreen());
        connect.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            int i = mv.getSelectionModel().getSelectedIndex();
            Socket s = connectMiner(miners.get(i));

            if (s == null) {
                result.setText("Couldn't connected to the miner");
                return;
            }
            result.setText("Connecting...");
            try {
                connectedMinerScreen(s, new ObjectInputStream(s.getInputStream()), new ObjectOutputStream(s.getOutputStream()));
            } catch (IOException e ) {
                result.setText("Couldn't connected to the miner");
            }
        });


        stage.setScene(new Scene(gridPane));
    }


    private void queryFromMinerScreen(String ip) {
        final int port = 4002;
        try {
            Socket s = new Socket(ip, port);
            ObjectInputStream in = new ObjectInputStream(s.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
            connectedMinerScreen(s, in, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void connectedMinerScreen(Socket s, ObjectInputStream in, ObjectOutputStream out) {
        try {
            out.writeObject(Job.GetFinishedElections);
            List<Election> fe = (List<Election>) in.readObject();

            out.writeObject(Job.GetOngoingElections);
            List<Election> elections = (List<Election>) in.readObject();

            GridPane gridPane = getGridPane();
            Text text = new Text("Elections:\n(double click to list below for refresh)");

            TextField pubKeyTextField = new TextField("public.key");
            Button open1 = new Button("Select from file system");

            TextField prvKeyTextField = new TextField("private.key");
            Button open2 = new Button("Select from file system");

            Button getHistoryButton = new Button("Get History");
            Button getResultButton = new Button("Get Results");

            Button prev = new Button("prev");

            Text ack = new Text();

            //list View for educational qualification
            ObservableList<String> names = FXCollections.observableArrayList();

            for (Election e : elections)
                names.add("(" + e.getId() + ", " + e.getName() + ") (Ongoing)");

            for (Election e : fe)
                names.add("(" + e.getId() + ", " + e.getName() + ") (Finished)");

            elections.addAll(fe);

            ListView<String> electionsListView = new ListView<>(names);

            gridPane.add(prev, 0, 0);
            gridPane.add(pubKeyTextField, 0, 1);
            gridPane.add(open1, 1, 1);
            gridPane.add(prvKeyTextField, 0, 2);
            gridPane.add(open2, 1, 2);
            gridPane.add(text, 0, 3);
            gridPane.add(electionsListView, 0, 4);
            gridPane.add(getHistoryButton, 0,  5);
            gridPane.add(getResultButton, 0,  6);
            gridPane.add(ack, 0,  8);


            prev.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                try {
                    s.close();
                } catch (IOException e) {
                }
                startScreen();
            });

            electionsListView.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getClickCount() == 2)
                    connectedMinerScreen(s, in, out);
            });

            getHistoryButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {

                if (pubKeyTextField.getText() != null && prvKeyTextField.getText() != null) {
                    try {
                        keyPair = new KeyPair(Crypto.readPublicKeyFromFile(pubKeyTextField.getText().trim()),
                                Crypto.readPrivateKeyFromFile(prvKeyTextField.getText().trim())
                        );
                    } catch (IOException e) {
                        ack.setText("Key pair couldn't read from:\n" +
                                pubKeyTextField.getText().trim() + "\n" +
                                prvKeyTextField.getText().trim());
                    }
                }
                try {
                    getHistoryFromMinerScreen(elections.get(electionsListView.getSelectionModel().getSelectedIndex()),
                            s, in, out);
                } catch (ArrayIndexOutOfBoundsException e) {
                    e.printStackTrace();
                }

            });

            getResultButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                getResultFromMinerScreen(elections.get(electionsListView.getSelectionModel().getSelectedIndex()),
                        s, in, out);
            });

            stage.setScene(new Scene(gridPane));
        } catch (IOException e) {
            disconnectedScreen();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void getResultFromMinerScreen(Election e, Socket s, ObjectInputStream in, ObjectOutputStream out) {
        Button prev = new Button("OK");
        Text text = new Text();
        GridPane gridPane = getGridPane();

        gridPane.add(text, 0, 0);
        gridPane.add(prev, 0, 1);

        prev.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> connectedMinerScreen(s, in, out));

        try {
            out.writeObject(new GetElectionResult(e.getId()));
            Object o = in.readObject();

            if (o.equals(Acknowledge.FAIL))
                text.setText("Not found");
            else
                text.setText(o.toString());
        } catch (IOException e1) {
            text.setText("Network error!");
        } catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        }

        stage.setScene(new Scene(gridPane));
    }

    private void getHistoryFromMinerScreen(Election e, Socket s, ObjectInputStream in, ObjectOutputStream out) {
        Button prev = new Button("OK");
        Text text = new Text();
        GridPane gridPane = getGridPane();

        gridPane.add(text, 0, 0);
        gridPane.add(prev, 0, 1);

        prev.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> connectedMinerScreen(s, in, out));

        GetVote v = new GetVote();
        v.setElectionID(e.getId());
        v.setKey(keyPair.getPublic().getEncoded());

        try {
            out.writeObject(v);
            Object o = in.readObject();

            if (o.equals(Acknowledge.FAIL))
                text.setText("Not found");
            else
                text.setText(o.toString());
        } catch (IOException e1) {
            text.setText("Network error!");
        } catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        }

        stage.setScene(new Scene(gridPane));
    }

    private Socket connectMiner(MinerInfo info) {
        final int PORT = 4002;
        Socket s = null;
        try {
            s = new Socket(info.getAddr(), PORT);
        } catch (IOException e) {
        }
        return s;
    }


    private void connectedScreen() {
        try {
            out.writeObject(Job.GetOngoingElections);
            MutablePair<Integer, String>[] ongoingElections = (MutablePair[]) in.readObject();

            GridPane gridPane = getGridPane();
            Text text = new Text("Ongoing elections:\n(double click to list below for refresh)");
            Button generateKeyPairButton = new Button("Generate key pair");
            Text info = new Text("");

            TextField pubKeyTextField = new TextField("public.key");
            Button open1 = new Button("Select from file system");

            TextField prvKeyTextField = new TextField("private.key");
            Button open2 = new Button("Select from file system");

            Button getTokenButton = new Button("Get Token");
            Button voteButton = new Button("Vote");
            Button getHistoryButton = new Button("Get History");
            Text ack = new Text();

            Button prev = new Button("Prev");

            //list View for educational qualification
            ObservableList<String> names = FXCollections.observableArrayList();
            for (MutablePair<Integer, String> e : ongoingElections)
                names.add("(" + e.left + ", " + e.right + ")");

            ListView<String> electionsListView = new ListView<>(names);

            gridPane.add(prev, 0, 0);
            gridPane.add(pubKeyTextField, 0, 1);
            gridPane.add(open1, 1, 1);
            gridPane.add(prvKeyTextField, 0, 2);
            gridPane.add(open2, 1, 2);
            gridPane.add(generateKeyPairButton, 0, 3);
            gridPane.add(info, 1, 3);
            gridPane.add(text, 0, 4);
            gridPane.add(electionsListView, 0, 5);
            gridPane.add(getTokenButton, 0, 6);
            gridPane.add(voteButton, 0, 7);
            gridPane.add(getHistoryButton, 0,  8);
            gridPane.add(ack, 0,  9);

            EventHandler<MouseEvent> getTokenEvent = new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    if (pubKeyTextField.getText() != null && prvKeyTextField.getText() != null) {
                        try {
                            keyPair = new KeyPair(Crypto.readPublicKeyFromFile(pubKeyTextField.getText().trim()),
                                    Crypto.readPrivateKeyFromFile(prvKeyTextField.getText().trim())
                            );
                        } catch (IOException e) {
                            ack.setText("Key pair couldn't read from:\n\"" +
                                    pubKeyTextField.getText().trim() + "\" and\n\"" +
                                    prvKeyTextField.getText().trim() + "\"");
                        }
                    }
                    getTokenScreen(new MutablePair<>(
                            ongoingElections[electionsListView.getSelectionModel().getSelectedIndex()].left,
                            electionsListView.getSelectionModel().getSelectedItem())
                    );
                }
            },
                    voteEvent = new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent event) {
                            if (pubKeyTextField.getText() != null && prvKeyTextField.getText() != null) {
                                try {
                                    keyPair = new KeyPair(Crypto.readPublicKeyFromFile(pubKeyTextField.getText().trim()),
                                            Crypto.readPrivateKeyFromFile(prvKeyTextField.getText().trim())
                                    );
                                } catch (IOException e) {
                                    ack.setText("Key pair couldn't read from:\n" +
                                            pubKeyTextField.getText().trim() + "\n" +
                                            prvKeyTextField.getText().trim());
                                }
                            }

                            voteScreen(new MutablePair<>(
                                    ongoingElections[electionsListView.getSelectionModel().getSelectedIndex()].left,
                                    electionsListView.getSelectionModel().getSelectedItem()));
                        }
                    },

                    getHistoryEvent = new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent event) {
                            if (pubKeyTextField.getText() != null && prvKeyTextField.getText() != null) {
                                try {
                                    keyPair = new KeyPair(Crypto.readPublicKeyFromFile(pubKeyTextField.getText().trim()),
                                            Crypto.readPrivateKeyFromFile(prvKeyTextField.getText().trim())
                                    );
                                } catch (IOException e) {
                                    ack.setText("Key pair couldn't read from:\n" +
                                            pubKeyTextField.getText().trim() + "\n" +
                                            prvKeyTextField.getText().trim());
                                }
                            }
                            try {
                                getHistoryScreen(new MutablePair<>(
                                        ongoingElections[electionsListView.getSelectionModel().getSelectedIndex()].left,
                                        electionsListView.getSelectionModel().getSelectedItem()));
                            } catch (ArrayIndexOutOfBoundsException e) {
                                e.printStackTrace();
                            }
                        }
                    },
                    open1Event = new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent event) {
                            try {
                                FileChooser fileChooser = new FileChooser();
                                fileChooser.setTitle("Public key chooser");
                                File file = fileChooser.showOpenDialog(stage);
                                pubKeyTextField.setText(file.getPath());
                            } catch (NullPointerException e) {}
                        }
                    },
                    open2Event = new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent event) {
                            try {
                                FileChooser fileChooser = new FileChooser();
                                fileChooser.setTitle("Private key chooser");
                                File file = fileChooser.showOpenDialog(stage);
                                prvKeyTextField.setText(file.getPath());
                            } catch (NullPointerException e) {}
                        }
                    },
                    generateKeyPairEvent = new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent event) {
                            String pubPath = pubKeyTextField.getText().trim(),
                                    prvPath = prvKeyTextField.getText().trim();

                            if (pubPath.isEmpty() || prvPath.isEmpty()) {
                                info.setText("Error: Public key path or private key path is empty");
                            } else {
                                keyPair = Crypto.generateKeyPair();
                                try {
                                    Common.writeTofile(keyPair.getPublic().getEncoded(), pubPath);
                                    Common.writeTofile(keyPair.getPrivate().getEncoded(), prvPath);
                                    info.setText("Done");
                                } catch (IOException e) {
                                    info.setText("Fail");
                                    e.printStackTrace();
                                }
                            }
                        }
                    };

            getTokenButton.addEventFilter(MouseEvent.MOUSE_CLICKED, getTokenEvent);
            voteButton.addEventFilter(MouseEvent.MOUSE_CLICKED, voteEvent);
            getHistoryButton.addEventFilter(MouseEvent.MOUSE_CLICKED, getHistoryEvent);
            open1.addEventFilter(MouseEvent.MOUSE_CLICKED, open1Event);
            open2.addEventFilter(MouseEvent.MOUSE_CLICKED, open2Event);
            generateKeyPairButton.addEventFilter(MouseEvent.MOUSE_CLICKED, generateKeyPairEvent);
            electionsListView.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getClickCount() == 2)
                    connectedScreen();
            });
            prev.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                try {
                    sslSocket.close();
                    sslSocket = null;
                    in = null;
                    out = null;
                } catch (IOException e) {
                }
                startScreen();
            });


            stage.setScene(new Scene(gridPane));
        } catch (IOException e) {
            disconnectedScreen();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void disconnectedScreen() {
        Text text = new Text("Server or miner disconnected!");
        Button button = new Button("Exit");
        GridPane gridPane = getGridPane();

        gridPane.add(text, 0, 0);
        gridPane.add(button, 0, 1);

        button.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> stage.close());

        stage.setScene(new Scene(gridPane));
    }

    private void getTokenScreen(MutablePair<Integer, String> election) {
        if (election == null)
            return;
        if (keyPair == null)
            return;

        Text text1 = new Text("Username");
        Text text2 = new Text("Password");
//        Text text3 = new Text("Token name");
        Text info = new Text("To voting with email address enter your email and\n" +
                "click \"Get One Time Password\" button.\n" +
                "One time password will be sent to your email.");
        Text result = new Text("");

        TextField usernameField = new TextField();
        TextField tokenField = new TextField("token_" + election.getRight());
        PasswordField passwordField = new PasswordField();
        Button getTokenButton = new Button("Get Token");
        Button getOTPButton = new Button("Get One Time Password");
        Button prevButton = new Button("Prev");
        HBox box = new HBox(10, passwordField, getOTPButton);
        //Creating a Grid Pane
        GridPane gridPane = getGridPane();


        //Arranging all the nodes in the grid
        gridPane.add(prevButton, 0, 0);
        gridPane.add(info, 1, 0);
        gridPane.add(text1, 0, 1);
        gridPane.add(usernameField, 1, 1);
        gridPane.add(text2, 0, 2);
        gridPane.add(box, 1, 2);
//        gridPane.add(text3, 0, 3);
//        gridPane.add(tokenField, 1, 3);
        gridPane.add(getTokenButton,1,  3);
        gridPane.add(result,1,  4);

        EventHandler<MouseEvent> handler = new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                try {
                    String uname = usernameField.getText().trim(),
                            pwd = passwordField.getText();

                    if (uname.isEmpty() || pwd.isEmpty()) {
                        result.setText("Error: Username or password empty");
                        return;
                    }

                    out.writeObject(Job.TokenCreation);
                    out.writeObject(election);
                    out.writeObject(new User(uname, pwd));

                    Object o = in.readObject();

                    if(o.equals(Acknowledge.SUCCESS)) {
                        PublicKey pk = (PublicKey) in.readObject();

                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        digest.update(keyPair.getPublic().getEncoded());
                        byte[] hash = digest.digest();

                        token = createToken(hash, pk);

                        writeTofile(token, tokenField.getText().trim());

                        result.setText("Done!");
                    } else {
                        result.setText("Invalid or used user");
                    }
                } catch (ClassNotFoundException | NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    disconnectedScreen();
                }
            }
        };

        getTokenButton.addEventFilter(MouseEvent.MOUSE_CLICKED, handler);
        prevButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            try {
                out.writeObject(Job.Cancel);
            } catch (IOException e) {
                disconnectedScreen();
            }
            connectedScreen();
        });

        getOTPButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            try {
                String uname = usernameField.getText().trim();

                if (uname.isEmpty()) {
                    System.out.println("Error: Username is empty");
                    return;
                }

                out.writeObject(Job.GetOTP);
                out.writeObject(election);
                out.writeObject(uname);

                Object o = in.readObject();

                if (o.equals(Acknowledge.SUCCESS))
                    result.setText("One time password sent to your email");
                else
                    result.setText("Invalid or used email address");
            } catch (IOException e) {
                disconnectedScreen();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        });


        stage.setScene(new Scene(gridPane));
    }

    private void voteScreen(MutablePair<Integer, String> election) {
        if (election == null)
            return;
        if (keyPair == null)
            return;

        try {
            out.writeObject(Job.GetElection);
            out.writeObject(election);

            Election e = (Election) in.readObject();

            GridPane gridPane = getGridPane();
            Text text = new Text("Select " + e.getNvotes_l() +
                    ((e.getNvotes_l() != e.getNvotes_h()) ? (" - " + e.getNvotes_h()) : "") +
                    " candidate(s) " +
                    "while ctrl key pressed\n" +
                    "Candidates:" );
            Text result = new Text();

            TextField tokenTextField = new TextField("token_" + election.getRight());
//            Button findTokenButton = new Button("Select from file system");
            Button voteButton = new Button("Vote");
            Button prevButton = new Button("Prev");

            //list View for educational qualification
            ObservableList<String> names = FXCollections.observableArrayList();

            for (IdNamePair p : e.getCandidates())
                names.add(p.getName());

            ListView<String> candidatesListView = new ListView<>(names);
            candidatesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            gridPane.add(prevButton, 0, 0);
//            gridPane.add(tokenTextField, 0, 1);
//            gridPane.add(findTokenButton, 1, 1);

            gridPane.add(text, 0, 1);
            gridPane.add(candidatesListView, 0, 2);
            gridPane.add(voteButton, 0, 3);
            gridPane.add(result, 0, 4);

            EventHandler<MouseEvent> handler = new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    try {
                        int n = candidatesListView.getSelectionModel().getSelectedItems().size();

                        if (n < e.getNvotes_l() || n > e.getNvotes_h()) {
                            result.setText("Error! You have to select " + e.getNvotes_l() +
                                    ((e.getNvotes_l() != e.getNvotes_h()) ? (" - " + e.getNvotes_h()) : "")  +
                                    " candidate(s).\n"
                                    + "You selected: " + n);

                            return;
                        }

                        out.writeObject(Job.Voting);
                        out.writeObject(election);

                        token = readTokenFromFile(tokenTextField.getText().trim());
                        PublicKey pk = Crypto.decodePublicKey((byte[]) in.readObject());

                        Vote v = new Vote();
                        v.setElectionID(election.getKey());
                        v.setEncodedKey(keyPair.getPublic().getEncoded());
                        v.setToken(token);

                        ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE / 8 * (n + 2) + Long.SIZE / 8);
                        buf.putInt(n);

                        List<Integer> inp = candidatesListView.getSelectionModel().getSelectedIndices();
                        int chksm = 0;

                        for (Integer i : inp) {
                            buf.putInt(i);
                            chksm += candidatesListView.getItems().get(i).hashCode();
                        }
                        buf.putInt(chksm);
                        long nonce = new Random().nextLong();
                        buf.putLong(nonce);
                        v.setVote(Crypto.encrypt(buf.array(), pk));
                        v.setSignature(Crypto.sign(keyPair.getPrivate(), v.getRawDataToSign()));

                        out.writeObject(v);

                        Object o = in.readObject();

                        if(o.equals(Acknowledge.SUCCESS))
                            result.setText("Done!");
                        else
                            result.setText("Fail");

                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }  catch (IOException e) {
                        disconnectedScreen();
                    }
                }
            },

                    findTokenEvent = new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent event) {
                            try {
                                FileChooser fileChooser = new FileChooser();
                                fileChooser.setTitle("Public key chooser");
                                File file = fileChooser.showOpenDialog(stage);
                                tokenTextField.setText(file.getPath());
                            } catch (NullPointerException e) {}
                        }
                    };

            voteButton.addEventFilter(MouseEvent.MOUSE_CLICKED, handler);
//            findTokenButton.addEventFilter(MouseEvent.MOUSE_CLICKED, findTokenEvent);
            prevButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                try {
                    out.writeObject(Job.Cancel);
                } catch (IOException e1) {
                    disconnectedScreen();
                }
                connectedScreen();
            });

            stage.setScene(new Scene(gridPane));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }  catch (IOException e) {
            disconnectedScreen();
        }
    }

    private void getHistoryScreen(MutablePair<Integer, String> election) {
        if (election == null)
            return;
        if (keyPair == null)
            return;

        Text text = new Text();
        Button prev = new Button("OK");
        GridPane gridPane = getGridPane();

        gridPane.add(text, 0, 0);
        gridPane.add(prev, 0, 1);

        try {
            out.writeObject(Job.GetVote);

            GetVote v = new GetVote();
            v.setElectionID(election.getKey());
            v.setKey(keyPair.getPublic().getEncoded());

            out.writeObject(v);
            Object o = in.readObject();

            if (o.equals(Acknowledge.FAIL))
                text.setText("Not found");
            else
                text.setText(o.toString());

        } catch (IOException e) {
            disconnectedScreen();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        prev.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> connectedScreen());
        stage.setScene(new Scene(gridPane));
    }

    private Token readTokenFromFile(String path) throws IOException, ClassNotFoundException {
        ObjectInputStream inp = new ObjectInputStream(new FileInputStream(path));
        Token t = (Token) inp.readObject();
        inp.close();
        return t;
    }


    private Token createToken(byte[] msg, PublicKey server_key) throws IOException, ClassNotFoundException {

        RSAKeyParameters server_pub = new RSAKeyParameters(false, ((RSAPublicKey) server_key).getModulus(),
                ((RSAPublicKey) server_key).getPublicExponent());

        //blinding parameters
        RSABlindingFactorGenerator blindingFactorGenerator = new RSABlindingFactorGenerator();
        blindingFactorGenerator.init(server_pub);
        BigInteger blindingFactor = blindingFactorGenerator.generateBlindingFactor();
        RSABlindingParameters blindingParams = new RSABlindingParameters(server_pub, blindingFactor);

        //blind data
        Digest digest = new SHA256Digest();
        PSSSigner signer = new PSSSigner(new RSABlindingEngine(), digest, digest.getDigestSize());
        signer.init(true, blindingParams);
        signer.update(msg, 0, msg.length);

        byte[] blindedData = null;
        try {
            blindedData = signer.generateSignature();
        } catch (CryptoException e) {
            e.printStackTrace();
        }

        //send server
        Token token = new Token();
        token.setData(blindedData);
        out.writeObject(token);

        token  = (Token) in.readObject();

        // unblind the signature
        RSABlindingEngine blindingEngine = new RSABlindingEngine();
        blindingEngine.init(false, blindingParams);
        byte[] sig = blindingEngine.processBlock(token.getSignature(), 0, token.getSignature().length);

        //set return value
        token.setData(msg);
        token.setSignature(sig);

        return token;
    }
}
