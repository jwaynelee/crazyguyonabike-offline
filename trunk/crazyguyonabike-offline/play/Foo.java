
public class Foo extends Thread {
	static String str = "Hello";

	public static void main(String[] args) throws InterruptedException {
		Foo foo = new Foo();
		foo.boo(str);
		System.out.println(str);
	}

	void boo(String str) throws InterruptedException {
		str = str + " " + "World";
		start();
		join();
	}

	@Override
	public void run() {
		for (int i = 0; i < 4; ++i) {
			str += " " + i;
		}
	}
}
