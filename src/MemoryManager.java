import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    Semaphore heapSemaphore = new Semaphore(1);

    // Statistics
    private final AtomicInteger totalWastedBytes = new AtomicInteger();
    private final AtomicInteger totalRequestsHandled = new AtomicInteger();
    private final AtomicInteger totalBytesAllocated = new AtomicInteger();
    private final AtomicInteger totalRequestsRemoved = new AtomicInteger();
    private final AtomicInteger releaseCalls = new AtomicInteger();

    public MemoryManager(int heapSizeKB, int pageSizeBytes){
        this.pageSizeBytes = pageSizeBytes;
        this.intsPerPage = pageSizeBytes / 4; // 1 int = 4 bytes
        // Total heap size (in ints and pages)
        int totalInts = (heapSizeKB * 1024) / 4;  // total heap size in ints
        this.totalPages = totalInts / intsPerPage;

        this.heap = new int[totalInts]; // the heap itself
        this.pageOccupied = new boolean[totalPages]; // page occupancy status
        this.allocationQueue = new ConcurrentLinkedQueue<>();
        // queue for FIFO policyP
    }

    // Attempts to allocate memory for a request
    public boolean allocate(Request request) {
        // Calculate how many pages are needed for the request
        int pagesNeeded = (int) Math.ceil((double) request.sizeBytes / pageSizeBytes);
        int bytesAllocated = pagesNeeded * pageSizeBytes;
        int wasted = bytesAllocated - request.sizeBytes;
        totalWastedBytes.addAndGet(wasted);

        // Try to find free pages
        List<Integer> freePages;
        try {
            heapSemaphore.acquire();
            freePages = findFreePages(pagesNeeded);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            heapSemaphore.release();
        }


        // Not enough space? Try to free memory
        if (freePages.size() < pagesNeeded) {
            try {
                heapSemaphore.acquire();
                releaseMemory();
                freePages = findFreePages(pagesNeeded);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                heapSemaphore.release();
            }

            // Still not enough space? Allocation fails
            if (freePages.size() < pagesNeeded) {
                return false; // Falha mesmo após liberar memória
            }
        }

        // Allocate the required number of pages
        List<Integer> selectedPages = freePages.subList(0, pagesNeeded);

        for (int pageIndex : selectedPages) {
            int start = pageIndex * intsPerPage;
            int end = start + intsPerPage;
            try {
                heapSemaphore.acquire();
                pageOccupied[pageIndex] = true;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }  finally {
                heapSemaphore.release();
            }
            for (int i = start; i < end; i++) {
                        heap[i] = request.id;
            }
        }

        // Update request metadata
        request.pagesAllocated = freePages.subList(0, pagesNeeded);
        request.timestamp = System.nanoTime(); // For FIFO order
        allocationQueue.add(request); // Add to allocation queue

        // Update statistics
        totalRequestsHandled.incrementAndGet();
        totalBytesAllocated.addAndGet(request.sizeBytes);

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
        releaseCalls.incrementAndGet();
        int pagesToFree = (int) Math.ceil(totalPages * 0.3);
        int pagesFreed = 0;

        // Remove oldest requests from the queue
        while (!allocationQueue.isEmpty() && pagesFreed < pagesToFree) {
            Request oldest = allocationQueue.poll();
            for (int pageIndex : oldest.pagesAllocated) {
                int start = pageIndex * intsPerPage;
                int end = start + intsPerPage;
                pageOccupied[pageIndex] = false;
                for (int i = start; i < end; i++) {
                    heap[i] = 0; // Clear memory
                }
                pagesFreed++;
            }
            totalRequestsRemoved.incrementAndGet();
        }
    }

    // Prints execution statistics
    public void printStats(long totalTimeMillis) {
        int handled = totalRequestsHandled.get();
        int allocated = totalBytesAllocated.get();
        int removed = totalRequestsRemoved.get();
        int releases = releaseCalls.get();
        int wasted = totalWastedBytes.get();

        System.out.println("\n--- Execution Statistics ---");
        System.out.println("Total requests handled: " + handled);
        System.out.printf("Average variable size: %.2f bytes%n",
                handled == 0 ? 0.0 : (double) allocated / handled);
        System.out.println("Total variables removed: " + removed);
        System.out.println("Memory release calls: " + releases);
        System.out.println("Total bytes unused (Internal fragmentation): " + wasted);
        System.out.printf("Average waste per allocation: %.2f%%\n",
                handled == 0 ? 0.0 : ((double) wasted / allocated) * 100);

        System.out.println("Total execution time: " + totalTimeMillis + "ms");

    }

    // Prints a visual representation of the heap using colored blocks
    public void printHeapVisual() {
        final String RESET = "\u001B[0m";
        final String GREEN = "\u001B[42m"; // Available
        final String BLUE = "\u001B[44m";  // ID
        final String ORANGE = "\u001B[43m"; // Mixed
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
