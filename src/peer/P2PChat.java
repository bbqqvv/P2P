package peer;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class P2PChat extends JFrame {
    private JTextPane chatArea;
    private JTextField inputField;
    private JTextField nameField;
    private int port;
    private String username;
    private ServerSocket serverSocket;
    private Map<Socket, PeerNode> peers;
    private Map<String, ChatGroup> chatGroups;
    private ExecutorService executor;
    private Set<String> activePeers;
    private DefaultListModel<String> groupListModel;
    private JList<String> groupList;
    private ChatGroup currentGroup;

    private JTextField peerIpField;
    private JTextField peerPortField;
    private JLabel chatStatusLabel;
    private JPanel statusPanel;
    private JLabel statusLabel;

    public P2PChat(int port, String username) {
        this.port = port;
        this.username = username;
        this.peers = new ConcurrentHashMap<>();
        this.executor = Executors.newCachedThreadPool();
        this.chatGroups = new HashMap<>();
        this.activePeers = new HashSet<>();

        setTitle("P2P Chat - " + username);
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Thanh trạng thái
        statusPanel = new JPanel();
        statusLabel = new JLabel("Trạng thái: Đang kết nối...");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);

        // Thêm các panel
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel nameLabel = new JLabel("Username: ");
        nameField = new JTextField(username);
        nameField.setEditable(false);
        gbc.gridx = 0;
        gbc.gridy = 0;
        topPanel.add(nameLabel, gbc);
        gbc.gridx = 1;
        topPanel.add(nameField, gbc);

        chatStatusLabel = new JLabel("Trạng thái: Đang trò chuyện chung");
        chatStatusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        topPanel.add(chatStatusLabel, gbc);

        add(topPanel, BorderLayout.NORTH);

        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(240, 240, 240));
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        inputField = new JTextField();
        inputField.setFont(new Font("Arial", Font.PLAIN, 14));
        inputField.addActionListener(e -> {
            String message = inputField.getText();
            if (!message.isEmpty()) {
                if (currentGroup != null) {
                    currentGroup.sendMessageToGroup(username + ": " + message);
                    appendToChatArea(username + ": " + message + "\n", Color.BLUE);
                } else {
                    sendMessageToAll(message);
                    appendToChatArea(username + ": " + message + "\n", Color.GREEN);
                }
                inputField.setText("");
                saveChatHistory(); // Lưu lịch sử trò chuyện
            }
        });

        add(inputField, BorderLayout.SOUTH);

        JPanel groupPanel = new JPanel();
        groupPanel.setLayout(new BorderLayout());
        groupListModel = new DefaultListModel<>();
        groupList = new JList<>(groupListModel);
        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedGroup = groupList.getSelectedValue();
                if (selectedGroup != null) {
                    ChatGroup group = chatGroups.get(selectedGroup);
                    StringBuilder membersList = new StringBuilder("Các thành viên: ");
                    for (PeerNode member : group.getMembers()) {
                        membersList.append(member.getSocket().getInetAddress().toString()).append(", ");
                    }
                    appendToChatArea(membersList.toString() + "\n", Color.BLACK);
                    joinGroup(selectedGroup);
                }
            }
        });

        JPopupMenu groupContextMenu = new JPopupMenu();
        JMenuItem inviteToGroupItem = new JMenuItem("Mời vào nhóm");
        inviteToGroupItem.addActionListener(e -> {
            String selectedGroup = groupList.getSelectedValue();
            if (selectedGroup != null) {
                showInviteDialog(selectedGroup);
            }
        });
        groupContextMenu.add(inviteToGroupItem);

        groupList.setComponentPopupMenu(groupContextMenu);
        groupPanel.add(new JScrollPane(groupList), BorderLayout.CENTER);

        JButton createGroupButton = new JButton("Tạo nhóm");
        createGroupButton.addActionListener(e -> {
            String groupName = JOptionPane.showInputDialog("Nhập tên nhóm:");
            if (groupName != null && !groupName.isEmpty()) {
                createGroup(groupName);
            }
        });
        groupPanel.add(createGroupButton, BorderLayout.NORTH);

        add(groupPanel, BorderLayout.EAST);

        JPanel connectPanel = new JPanel();
        connectPanel.setLayout(new FlowLayout());
        JLabel ipLabel = new JLabel("IP peer:");
        peerIpField = new JTextField(10);
        JLabel portLabel = new JLabel("Port peer:");
        peerPortField = new JTextField(5);
        JButton connectButton = new JButton("Kết nối với peer");

        connectButton.addActionListener(e -> {
            String peerIp = peerIpField.getText().trim();
            String peerPort = peerPortField.getText().trim();
            if (!peerIp.isEmpty() && !peerPort.isEmpty()) {
                connectToPeer(peerIp, Integer.parseInt(peerPort));
            } else {
                appendToChatArea("Vui lòng nhập đầy đủ IP và Port.\n", Color.RED);
            }
        });

        connectPanel.add(ipLabel);
        connectPanel.add(peerIpField);
        connectPanel.add(portLabel);
        connectPanel.add(peerPortField);
        connectPanel.add(connectButton);

        add(connectPanel, BorderLayout.NORTH);

        startServer();
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            appendToChatArea("Đang lắng nghe trên cổng " + port + "\n", Color.BLACK);
            updateStatus("Đang kết nối...");

            executor.execute(() -> {
                while (true) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        PeerNode peer = new PeerNode(clientSocket);
                        peers.put(clientSocket, peer);
                        appendToChatArea("Peer mới kết nối: " + clientSocket.getInetAddress() + "\n", Color.BLACK);
                        activePeers.add(clientSocket.getInetAddress().toString());

                        executor.execute(() -> receiveMessagesFromPeer(peer));
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void createGroup(String groupName) {
        if (!chatGroups.containsKey(groupName)) {
            ChatGroup group = new ChatGroup(groupName);
            chatGroups.put(groupName, group);
            groupListModel.addElement(groupName);
            appendToChatArea("Tạo nhóm mới: " + groupName + "\n", Color.BLACK);
        } else {
            appendToChatArea("Nhóm " + groupName + " đã tồn tại.\n", Color .RED);
        }
    }

    public void joinGroup(String groupName) {
        if (chatGroups.containsKey(groupName)) {
            currentGroup = chatGroups.get(groupName);
            appendToChatArea("Đã tham gia nhóm: " + groupName + "\n", Color.BLACK);
            currentGroup.sendMessageToGroup(username + " đã tham gia nhóm.");
            updateGroupList();
            chatStatusLabel.setText("Trạng thái: Đang trò chuyện trong nhóm " + groupName);
        } else {
            appendToChatArea("Nhóm " + groupName + " không tồn tại.\n", Color.RED);
        }
    }

    private void showInviteDialog(String groupName) {
        List<String> peerList = new ArrayList<>(peers.keySet().stream()
            .map(socket -> socket.getInetAddress().toString())
            .collect(Collectors.toList()));

        String[] peerAddresses = peerList.toArray(new String[0]);

        String selectedPeer = (String) JOptionPane.showInputDialog(
                this,
                "Chọn peer để mời",
                "Mời vào nhóm",
                JOptionPane.PLAIN_MESSAGE,
                null,
                peerAddresses,
                peerAddresses.length > 0 ? peerAddresses[0] : null);

        if (selectedPeer != null) {
            inviteToGroup(groupName, selectedPeer);
        }
    }

    private void inviteToGroup(String groupName, String peerAddress) {
        PeerNode peer = peers.values().stream()
                .filter(p -> p.getSocket().getInetAddress().toString().equals(peerAddress))
                .findFirst().orElse(null);

        if (peer != null) {
            appendToChatArea("Mời " + peerAddress + " tham gia nhóm: " + groupName + "\n", Color.BLACK);
            peer.sendMessage("Bạn đã được mời tham gia nhóm: " + groupName);
            currentGroup.addMember(peer);
            List<String> groupNames = new ArrayList<>(chatGroups.keySet());
            peer.sendGroupList(groupNames);
            updateGroupList();
            chatStatusLabel.setText("Trạng thái: Đang trò chuyện trong nhóm " + groupName);
        }
    }

    private void connectToPeer(String ip, int port) {
        try {
            Socket socket = new Socket(ip, port);
            PeerNode peer = new PeerNode(socket);
            peers.put(socket, peer);
            appendToChatArea("Kết nối với peer tại " + ip + ":" + port + "\n", Color.BLACK);
            executor.execute(() -> receiveMessagesFromPeer(peer));
            chatStatusLabel.setText("Trạng thái: Đang trò chuyện riêng với " + ip + ":" + port);
        } catch (IOException ex) {
            appendToChatArea("Kết nối thất bại với peer tại " + ip + ":" + port + "\n", Color.RED);
            ex.printStackTrace();
        }
    }

    private void sendMessageToAll(String message) {
        for (PeerNode peer : peers.values()) {
            executor.execute(() -> peer.sendMessage(message));
        }
    }

    private void appendToChatArea(String message, Color color) {
        try {
            StyledDocument doc = chatArea.getStyledDocument();
            Style style = chatArea.addStyle("style", null);
            StyleConstants.setForeground(style, color);
            doc.insertString(doc.getLength(), message, style);
            chatArea.setCaretPosition(doc.getLength()); // Cuộn xuống cuối khi có tin nhắn mới
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void updateStatus(String status) {
        statusLabel.setText("Trạng thái: " + status);
    }

    private void updateGroupList() {
        groupListModel.clear();
        for (String groupName : chatGroups.keySet()) {
            ChatGroup group = chatGroups.get(groupName);
            String groupInfo = groupName + " (" + group.getMembers().size() + " thành viên)";
            groupListModel.addElement(groupInfo);
        }
    }

    private void receiveMessagesFromPeer(PeerNode peer) {
        try {
            String message;
            while ((message = peer.receiveMessage()) != null) {
                if (message.startsWith("GROUP_LIST")) {
                    String groupListMessage = message.substring(11);
                    String[] groupNames = groupListMessage.split(",");
                    updatePeerGroupList(groupNames);
                } else {
                    appendToChatArea("Peer: " + message + "\n", Color.BLACK);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

   

    private void updatePeerGroupList(String[] groupNames) {
    	 groupListModel.clear();
         for (String groupName : groupNames) {
             groupListModel.addElement(groupName);
             }
	}

	public static void main(String[] args) {
        String username = JOptionPane.showInputDialog("Nhập tên người dùng:");
        if (username != null && !username.isEmpty()) {
            int port = Integer.parseInt(JOptionPane.showInputDialog("Nhập cổng:"));
            P2PChat chatApp = new P2PChat(port, username);
            chatApp.setVisible(true);
        }
    }

    // Thêm chức năng lưu lịch sử trò chuyện vào file
    private void saveChatHistory() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("chat_history.txt", true))) {
            writer.write(chatArea.getText());
            writer.newLine();
        } catch (IOException e) {
            appendToChatArea("Lỗi khi lưu lịch sử trò chuyện.\n", Color.RED);
        }
    }

   
    // Thêm thông báo khi có tin nhắn mới
    private void notifyNewMessage(String message) {
        JOptionPane.showMessageDialog(this, "Tin nhắn mới: " + message, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }


}