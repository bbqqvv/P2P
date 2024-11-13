package peer;

import java.util.*;

class ChatGroup {
    private String name;
    private Set<PeerNode> members;

    public ChatGroup(String name) {
        this.name = name;
        this.members = new HashSet<>();
    }

    public String getName() {
        return name;
    }

    public void addMember(PeerNode peer) {
        members.add(peer);
        sendMessageToGroup(peer.getUsername() + " has joined the group.");
    }

    public void removeMember(PeerNode peer) {
        members.remove(peer);
        sendMessageToGroup(peer.getUsername() + " has left the group.");
    }

    public void sendMessageToGroup(String message) {
        for (PeerNode member : members) {
            member.sendMessage(message); // Send the message to all members
        }
    }

    public Set<PeerNode> getMembers() {
        return members;
    }

    public void sendPrivateMessage(PeerNode sender, String message) {
        for (PeerNode member : members) {
            if (!member.equals(sender)) {
                member.sendMessage(sender.getUsername() + " (private): " + message);
            }
        }
    }
}
