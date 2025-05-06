import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// This class is responsible for generating a list of random memory allocation requests
public class RequestGenerator {
    private final int minSize; // Minimum size (in bytes) for a request
    private final int maxSize; // Maximum size (in bytes) for a request
    private final int totalRequests; // Total number of requests to generate
    private final Random random; // Random number generator

    // Constructor to initialize request generation parameters
    public RequestGenerator(int totalRequests, int minSize, int maxSize) {
        this.totalRequests = totalRequests;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.random = new Random();
    }

    // Generates a list of random requests with unique IDs and random sizes
    public List<Request> generate() {
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            // Generate a random size within the specified range
            int size = random.nextInt(maxSize - minSize + 1) + minSize;
            // Create a new request with a unique ID (starting from 1)
            requests.add(new Request(i + 1, size));
        }
        return requests;
    }
}
