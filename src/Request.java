import java.util.List;

// Represents a memory allocation request
public class Request {
    public final int id; // Unique ID for the request
    public final int sizeBytes; // Size of the request in bytes
    public List<Integer> pagesAllocated; // List of page indices allocated to this request
    public long timestamp; // Timestamp for FIFO ordering (set during allocation)

    // Constructor to initialize request with an ID and size
    public Request(int id, int sizeBytes) {
        this.id = id;
        this.sizeBytes = sizeBytes;
    }
}
