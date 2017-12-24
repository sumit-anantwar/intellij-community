package jetCheck;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static jetCheck.Generator.*;

/**
 * @author peter
 */
public class GeneratorTest extends PropertyCheckerTestCase {

  public void testMod() {
    checkFalsified(integers(),
                   i -> i % 12 != 0,
                   1);
  }

  public void testListSumMod() {
    checkFalsified(nonEmptyLists(integers()),
                   l -> l.stream().mapToInt(Integer::intValue).sum() % 10 != 0,
                   181);
  }

  public void testListContainsDivisible() {
    checkGeneratesExample(nonEmptyLists(integers()),
                          l -> l.stream().anyMatch(i -> i % 10 == 0),
                          4);
  }

  public void testStringContains() {
    assertEquals("a", checkGeneratesExample(stringsOf(asciiPrintableChars()),
                                            s -> s.contains("a"),
                                            8));
  }

  public void testLetterStringContains() {
    checkFalsified(stringsOf(asciiLetters()),
                   s -> !s.contains("a"),
                   1);
  }

  public void testIsSorted() {
    PropertyFailure<List<Integer>> failure = checkFalsified(nonEmptyLists(integers()),
                                                            l -> l.stream().sorted().collect(Collectors.toList()).equals(l),
                                                            34);
    List<Integer> value = failure.getMinimalCounterexample().getExampleValue();
    assertEquals(2, value.size());
    assertTrue(value.toString(), value.stream().allMatch(i -> Math.abs(i) < 2));
  }

  public void testSuccess() {
    PropertyChecker.forAll(listsOf(integers(-1, 1))).shouldHold(l -> l.stream().allMatch(i -> Math.abs(i) <= 1));
  }

  public void testSortedDoublesNonDescending() {
    PropertyFailure<List<Double>> failure = checkFalsified(listsOf(doubles()),
                                                           l -> isSorted(l.stream().sorted().collect(Collectors.toList())),
                                                           44);
    assertEquals(2, failure.getMinimalCounterexample().getExampleValue().size());
  }

  private static boolean isSorted(List<Double> list) {
    for (int i = 0; i < list.size() - 1; i++) {
      double d1 = list.get(i);
      double d2 = list.get(i + 1);
      if (!(d1 <= d2)) return false;
    }
    return true;
  }

  public void testSuchThat() {
    PropertyChecker.forAll(integers().suchThat(i -> i < 0)).shouldHold(i -> i < 0);
  }

  public void testNestedSometimesVeryRareSuchThat() {
    forAllStable(frequency(50, constant(0), 1, integers(1, 1000)).suchThat(i -> i > 0)).shouldHold(i -> i > 0);
  }

  public void testStringOfStringChecksAllChars() {
    checkFalsified(stringsOf("abc "),
                   s -> !s.contains(" "),
                   0);
  }

  public void testListNotLongerThanMaxDefaultSize() {
    PropertyChecker.forAll(listsOf(integers())).withIterationCount(1_000).shouldHold(l -> l.size() <= PropertyChecker.DEFAULT_MAX_SIZE_HINT);
  }

  public void testNonEmptyList() {
    PropertyChecker.forAll(nonEmptyLists(integers())).shouldHold(l -> !l.isEmpty());
  }

  public void testNoDuplicateData() {
    Set<List<Integer>> visited = new HashSet<>();
    PropertyChecker.forAll(listsOf(integers())).shouldHold(l -> visited.add(l));
  }

  public void testOneOf() {
    List<Integer> values = new ArrayList<>();
    PropertyChecker.forAll(anyOf(integers(0, 1), integers(10, 1100))).shouldHold(i -> values.add(i));
    assertTrue(values.stream().anyMatch(i -> i < 2));
    assertTrue(values.stream().anyMatch(i -> i > 5));
  }

  public void testAsciiIdentifier() {
    PropertyChecker.forAll(asciiIdentifiers())
      .shouldHold(s -> Character.isJavaIdentifierStart(s.charAt(0)) && s.chars().allMatch(Character::isJavaIdentifierPart));
    checkGeneratesExample(asciiIdentifiers(),
                          s -> s.contains("_"),
                          11);
  }

  public void testBoolean() {
    List<Boolean> list = checkGeneratesExample(listsOf(booleans()),
                                               l -> l.contains(true) && l.contains(false),
                                               4);
    assertEquals(2, list.size());
  }

  public void testShrinkingNonEmptyList() {
    List<Integer> list = checkGeneratesExample(nonEmptyLists(integers(0, 100)),
                                               l -> l.contains(42),
                                               12);
    assertEquals(1, list.size());
  }

  public void testRecheckWithGivenSeeds() {
    Generator<List<Integer>> gen = nonEmptyLists(integers(0, 100));
    Predicate<List<Integer>> property = l -> !l.contains(42);

    PropertyFailure<?> failure = checkFails(PropertyChecker.forAll(gen).withSeed(1), property).getFailure();
    assertTrue(failure.getIterationNumber() > 1);

    PropertyFalsified e;

    e = checkFails(PropertyChecker.forAll(gen).rechecking(failure.getIterationSeed(), failure.getSizeHint()), property);
    assertEquals(1, e.getFailure().getIterationNumber());

    e = checkFails(PropertyChecker.forAll(gen).withSeed(failure.getGlobalSeed()), property);
    assertEquals(failure.getIterationNumber(), e.getFailure().getIterationNumber());
  }

  public void testShrinkingComplexString() {
    checkFalsified(listsOf(stringsOf(asciiPrintableChars())),
                   l -> {
                     String s = l.toString();
                     return !"abcdefghijklmnopqrstuvwxyz()[]#!".chars().allMatch(c -> s.indexOf((char)c) >= 0);
                   },
                   225);
  }

  public void testSameFrequency() {
    checkFalsified(listsOf(frequency(1, constant(1), 1, constant(2))),
                   l -> !l.contains(1) || !l.contains(2),
                   3);

    checkFalsified(listsOf(frequency(1, constant(1), 1, constant(2)).with(1, constant(3))),
                   l -> !l.contains(1) || !l.contains(2) || !l.contains(3),
                   8);
  }

}
