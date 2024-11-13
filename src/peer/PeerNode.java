package peer;

import java.io.*;
import java.net.*;
import java.util.List;

class PeerNode {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    public PeerNode(Socket socket) {
        this.socket = socket;
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.username = socket.getInetAddress().toString();  // For simplicity, use IP as username
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public String getUsername() {
        return username;
    }

    public void sendMessage(String message) {
        out.println(message);  // Send message to the peer
    }

    public String receiveMessage() throws IOException {
        return in.readLine();  // Read message from the peer
    }
    // Phương thức gửi danh sách nhóm
    public void sendGroupList(List<String> groupNames) {
        String groupListMessage = "GROUP_LIST " + String.join(",", groupNames);
        sendMessage(groupListMessage);
    }
}
