// "Replace with collect" "true-preview"

import java.util.ArrayList;
import java.util.List;

public class Main {
  List<String> test(List<String> list) {
    List<String> result = new ArrayList<>();
    for(String s : li<caret>st) {
      if(result.contains(s)) {
        continue;
      }
      if(s.contains("foo")) {
        continue;
      }
      result.add(s);
    }
    return result;
  }
}
