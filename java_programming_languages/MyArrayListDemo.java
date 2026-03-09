/**
 * CUSTOM ARRAYLIST - Shows how ArrayList works internally
 * Key concepts: Dynamic array, capacity management, resizing
 */
public class MyArrayListDemo {
    public static void main(String[] args) {
        MyArrayList<String> list = new MyArrayList<>();

        // Add elements - watch capacity grow
        System.out.println("=== ADDING ELEMENTS ===");
        for (int i = 1; i <= 12; i++) {
            list.add("Item" + i);
            if (i == 1 || i == 10 || i == 11) {
                System.out.printf("Added %d items - Size: %d, Capacity: %d\n",
                                  i, list.size(), list.capacity());
            }
        }

        // Get element
        System.out.println("\n=== GET ===");
        System.out.println("Get index 5: " + list.get(5));

        // Remove element
        System.out.println("\n=== REMOVE ===");
        String removed = list.remove(0);
        System.out.println("Removed: " + removed);
        System.out.println("Size after remove: " + list.size());

        // Display all
        System.out.println("\n=== ALL ELEMENTS ===");
        System.out.println(list);
    }
}

/**
 * Custom ArrayList implementation
 * Mimics java.util.ArrayList behavior
 */
class MyArrayList<E> {
    private static final int DEFAULT_CAPACITY = 10;
    private Object[] data;      // Internal array to store elements
    private int size;           // Current number of elements

    public MyArrayList() {
        data = new Object[DEFAULT_CAPACITY];
        size = 0;
    }

    // Add element at end - O(1) amortized, O(n) when resizing
    public boolean add(E element) {
        ensureCapacity();       // Grow array if needed
        data[size++] = element; // Add and increment size
        return true;
    }

    // Get element by index - O(1)
    @SuppressWarnings("unchecked")
    public E get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        return (E) data[index];
    }

    // Remove element by index - O(n) due to shifting
    @SuppressWarnings("unchecked")
    public E remove(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        E removed = (E) data[index];

        // Shift elements left to fill gap
        for (int i = index; i < size - 1; i++) {
            data[i] = data[i + 1];
        }

        data[--size] = null; // Clear last element and decrement size
        return removed;
    }

    // Ensure capacity - grow array when full
    private void ensureCapacity() {
        if (size == data.length) {
            int newCapacity = data.length * 2; // Double the capacity
            Object[] newData = new Object[newCapacity];

            // Copy old data to new array
            for (int i = 0; i < size; i++) {
                newData[i] = data[i];
            }

            data = newData;
            System.out.println("  [RESIZED] Capacity: " + data.length);
        }
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return data.length;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            sb.append(data[i]);
            if (i < size - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}

