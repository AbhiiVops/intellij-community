// "Merge with 'case default'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      case default:
        System.out.println(42);
        break;
      case null:
        System.out.<caret>println(42);
    }
  }
}
