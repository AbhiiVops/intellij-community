// "Make variable effectively final" "true-preview"
class X {
  void test(boolean a, int y) {
    int x;
    if (a) {
      x = 1;
    } else {
        x = y;
    }
      Runnable r = () -> System.out.println(x);
  }
}