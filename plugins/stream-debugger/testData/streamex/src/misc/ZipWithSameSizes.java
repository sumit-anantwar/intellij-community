package misc;

import one.util.streamex.StreamEx;

import java.util.stream.Stream;

public class ZipWithSameSizes {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3).zipWith(Stream.of(1, 3, 9)).count();
    System.out.println(count);
  }
}
