// "Unwrap 'if' statement extracting side effects" "true-preview"
class Test {
  void foo(Object obj) {
    if (!(obj instanceof Rect)) {
      return;
    }
    if (<caret>obj instanceof Rect(Point(double x, double y) pos, Size(double w, double h) size) rect) {
      System.out.println(rect);
      System.out.println(rect.size());
      System.out.println(pos);
      System.out.println(pos.x());
      System.out.println(h);
    }
  }
}

record Point(double x, double y) {
}

record Size(double w, double h) {
}

record Rect(Point pos, Size size) {
}
