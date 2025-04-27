import java.io.Serializable;

public class Message implements Serializable {
    static final long serialVersionUID = 42L;

    private MessageType type;
    private Serializable content;
    private int row, col;
    private int recipient;

    public Message(MessageType type, Serializable content) {
        this.type = type;
        this.content = content;
        this.recipient = -1;
    }

    public Message(MessageType type, Serializable content, int recipient) {
        this.type = type;
        this.content = content;
        this.recipient = recipient;
    }


    public MessageType getType() { return type; }
    public Serializable getContent() { return content; }
    public int getRecipient() { return recipient; }


}