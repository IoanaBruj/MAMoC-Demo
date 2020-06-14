package uk.ac.standrews.cs.mamoc_demo.Fibonacci;

import uk.ac.standrews.cs.mamoc_client.Annotation.Offloadable;

@Offloadable
public class Fibonacci {

    private int n;

    public Fibonacci(int N){
        this.n = N;
    }

    public void run() {
        fibonacciRecursion(n);
    }

    private static Integer fibonacciRecursion(Integer number) {

        if (number == 1 || number == 2) {
            return 1;
        }
        return fibonacciRecursion(number - 1) + fibonacciRecursion(number - 2);
    }
}
