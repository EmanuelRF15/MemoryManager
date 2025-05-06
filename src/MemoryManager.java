import java.util.*;

public class MemoryManager {
    // Represents the heap as an array of integers (each int = 4 bytes)
    private final int[] heap;

    // Page size in bytes and how many integers fit in one page
    private final int pageSizeBytes;
    private final int intsPerPage;

    // FIFO queue to manage memory allocation order
    private final Queue<Request> allocationQueue;

    private final boolean[] pageOccupied;
    private final int totalPages;

    // Statistics
    private int totalRequestsHandled = 0;
    private int totalBytesAllocated = 0;
    private int totalRequestsRemoved = 0;
    private int releaseCalls = 0;

    public MemoryManager(int heapSizeKB, int pageSizeBytes){
        this.pageSizeBytes = pageSizeBytes;
        this.intsPerPage = pageSizeBytes / 4; // 1 int = 4 bytes
        // Total heap size (in ints and pages)
        int totalInts = (heapSizeKB * 1024) / 4;  // total heap size in ints
        this.totalPages = totalInts / intsPerPage;

        this.heap = new int[totalInts]; // the heap itself
        this.pageOccupied = new boolean[totalPages]; // page occupancy status
        this.allocationQueue = new LinkedList<>(); // queue for FIFO policy
    }

    // Attempts to allocate memory for a request
    public boolean allocate(Request request) {
        // Calculate how many pages are needed for the request
        int pagesNeeded = (int) Math.ceil((double) request.sizeBytes / pageSizeBytes);

        // Try to find free pages
        List<Integer> freePages = findFreePages(pagesNeeded);

        // Not enough space? Try to free memory
        if (freePages.size() < pagesNeeded) {
            releaseMemory(); // libera espaço se necessário
            freePages = findFreePages(pagesNeeded);

            // Still not enough space? Allocation fails
            if (freePages.size() < pagesNeeded) {
                return false; // Falha mesmo após liberar memória
            }
        }

        // Allocate the required number of pages
        for (int pageIndex : freePages.subList(0, pagesNeeded)) {
            pageOccupied[pageIndex] = true;
            int start = pageIndex * intsPerPage;
            int end = start + intsPerPage;
            for (int i = start; i < end; i++) {
                heap[i] = request.id; // Fill the heap with the request ID
            }
        }

        // Update request metadata
        request.pagesAllocated = freePages.subList(0, pagesNeeded);
        request.timestamp = System.nanoTime(); // For FIFO order
        allocationQueue.add(request); // Add to allocation queue

        // Update statistics
        totalRequestsHandled++;
        totalBytesAllocated += request.sizeBytes;

        return true;
    }

    // Finds the specified number of free pages
    private List<Integer> findFreePages(int required) {
        List<Integer> freePages = new ArrayList<>();
        for (int i = 0; i < totalPages && freePages.size() < required; i++) {
            if (!pageOccupied[i]) {
                freePages.add(i);
            }
        }
        return freePages;
    }

    // Frees memory using FIFO until at least 30% of the heap is freed
    private void releaseMemory() {
        releaseCalls++;
        int pagesToFree = (int) Math.ceil(totalPages * 0.3);
        int pagesFreed = 0;

        // Remove oldest requests from the queue
        while (!allocationQueue.isEmpty() && pagesFreed < pagesToFree) {
            Request oldest = allocationQueue.poll();
            for (int pageIndex : oldest.pagesAllocated) {
                pageOccupied[pageIndex] = false;
                int start = pageIndex * intsPerPage;
                int end = start + intsPerPage;
                for (int i = start; i < end; i++) {
                    heap[i] = 0; // Clear memory
                }
                pagesFreed++;
            }
            totalRequestsRemoved++;
        }
    }

    // Prints execution statistics
    public void printStats(long totalTimeMillis) {
        System.out.println("\n--- Execution Statistics ---");
        System.out.println("Total requests handled: " + totalRequestsHandled);
        System.out.printf("Average variable size: %.2f bytes%n",
                totalRequestsHandled == 0 ? 0.0 : (double) totalBytesAllocated / totalRequestsHandled);
        System.out.println("Total variables removed: " + totalRequestsRemoved);
        System.out.println("Memory release calls: " + releaseCalls);
        System.out.println("Total execution time: " + totalTimeMillis + "ms");
    }

    // Prints a visual representation of the heap using colored blocks
    public void printHeapVisual() {
        final String RESET = "\u001B[0m";
        final String GREEN = "\u001B[42m"; // LIVRE
        final String BLUE = "\u001B[44m";  // ID ÚNICO
        final String ORANGE = "\u001B[43m"; // MISTO
        final String BLACK_TEXT = "\u001B[30m";

        System.out.println("\n--- Heap Visualization (each block = 1 page) ---");
        for (int i = 0; i < totalPages; i++) {
            int start = i * intsPerPage;
            int end = start + intsPerPage;
            int id = heap[start];
            boolean same = true;

            // Check if all ints in this page have the same ID
            for (int j = start + 1; j < end; j++) {
                if (heap[j] != id) {
                    same = false;
                    break;
                }
            }

            String color;
            String text;
            if (!pageOccupied[i]) {
                color = GREEN;
                text = "   "; // Free page
            } else if (same) {
                color = BLUE;
                text = String.format("%3d", id % 1000); // Show last 3 digits of ID
            } else {
                color = ORANGE;
                text = " ? "; // Mixed page
            }

            System.out.print(color + BLACK_TEXT + text + RESET);

            // Print 16 pages per line for readability
            if ((i + 1) % 16 == 0) {
                System.out.println();
            }
        }
        System.out.println();
    }

}
