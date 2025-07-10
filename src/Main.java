import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // --- User input for memory and request configuration ---
        System.out.print("Enter heap size (in KB): ");
        int heapSizeKB = scanner.nextInt();

        System.out.print("Number of memory allocation requests to generate: ");
        int totalRequests = scanner.nextInt();

        System.out.print("Minimum variable size (in bytes) [default = 16]: ");
        int minSize = scanner.hasNextInt() ? scanner.nextInt() : 16;

        System.out.print("Maximum variable size (in bytes) [default = 1024]: ");
        int maxSize = scanner.hasNextInt() ? scanner.nextInt() : 1024;

        System.out.print("Page size(in bytes) [default = 64]: ");
        int pageSizeBytes = scanner.hasNextInt() ? scanner.nextInt() : 64;

        // --- Initialization of memory manager and request generator ---
        MemoryManager memoryManager = new MemoryManager(heapSizeKB, pageSizeBytes);
        RequestGenerator generator = new RequestGenerator(totalRequests, minSize, maxSize);
        List<Request> requests = generator.generate();

        System.out.println("\nStarting memory allocation...\n");

        // --- Begin timing the allocation process ---
        long startTime = System.currentTimeMillis();

        // --- Try to allocate memory for each request ---
        List<Thread> threads = new ArrayList<>();

        for (Request request : requests) {
            Thread thread = new Thread(() -> {
                boolean success = memoryManager.allocate(request);
                if (!success) {
                    System.out.println("Error: Failed to allocate request ID " + request.id);
                }
            });

            threads.add(thread);
            thread.start();
        }

        // --- Wait to until every thread ends ---
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread interrupted while waiting.");
            }
        }
        long endTime = System.currentTimeMillis();
        long elapsed = endTime - startTime;

        // --- Display summary statistics of the execution ---
        memoryManager.printStats(elapsed);

        // --- Optional heap visualization ---
        System.out.print("Would you like to visually display the final state of heap? (y/n): ");
        String visual = scanner.next();
        if (visual.equalsIgnoreCase("s") || visual.equalsIgnoreCase("y")) {
            memoryManager.printHeapVisual();
        }
    }
}
