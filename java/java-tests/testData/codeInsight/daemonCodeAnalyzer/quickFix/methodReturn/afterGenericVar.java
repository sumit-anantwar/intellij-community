// "Make 'method' return 'java.util.List<T>'" "true"
import java.util.*;

public class Test {
  class Inner<T> {
    List<T> method() {
        return null;
    }

    void test() {
      List<T> t = method();
    }
  }
}
