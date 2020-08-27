import java.util.Arrays;

public class Test {
	public static void main(String[] args) {
		int[] ints = {0, 1, 2};
		Integer[] integers = {1, 2};
		System.out.println(ints.getClass().getComponentType());
		System.out.println(integers.getClass().getComponentType());
		for (Object o : Arrays.asList((int[]) ints)) {
			System.out.println(o);
		}
	}
}
