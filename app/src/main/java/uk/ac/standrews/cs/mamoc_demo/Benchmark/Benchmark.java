package uk.ac.standrews.cs.mamoc_demo.Benchmark;

import uk.ac.standrews.cs.mamoc_client.Annotation.Offloadable;

@Offloadable
public class Benchmark {

    private static final int FFT_SIZE = 1024;
    private static final int SOR_SIZE = 100; // NxN grid
//    private static final int SIZE_M = 100;
//    private static final int SIZE_NZ = 500;
    private static final int LU_SIZE = 100;

    private static final double MIN_TIME = 2.0;
    private static final int RANDOM_SEED = 101010;

    Random R = new Random(RANDOM_SEED);

    public double run() {

        double fftResult = measureFFT(FFT_SIZE, R);
        double sorResult = measureSOR(SOR_SIZE, R);
        double montecarloResult = measureMonteCarlo(R);
//        double sparseResult = measureSparseMat(SIZE_M, SIZE_NZ, R);
        double luResult = measureLU(LU_SIZE, R);

        return (fftResult + sorResult + montecarloResult+ luResult) / 4.0; // + sparseResult
    }

    private double measureFFT(int N, Random R) {
        // initialize FFT data as complex (N real/img pairs)

        double x[] = RandomVector(2 * N, R);

        long cycles = 1;
        Stopwatch Q = new Stopwatch();

        while (true) {
            Q.start();
            for (int i = 0; i < cycles; i++) {
                FFT.transform(x);    // forward transform
                FFT.inverse(x);        // backward transform
            }
            Q.stop();
            if (Q.read() >= MIN_TIME)
                break;

            cycles *= 2;
        }
        // approx Mflops

        final double EPS = 1.0e-10;
        if (FFT.test(x) / N > EPS)
            return 0.0;

        return FFT.num_flops(N) * cycles / Q.read() * 1.0e-6;
    }

    private static double measureSOR(int N, Random R) {
        double G[][] = RandomMatrix(N, N, R);

        Stopwatch Q = new Stopwatch();
        int cycles = 1;
        while (true) {
            Q.start();
            SOR.execute(1.25, G, cycles);
            Q.stop();
            if (Q.read() >= MIN_TIME) break;

            cycles *= 2;
        }
        // approx Mflops
        return SOR.num_flops(N, N, cycles) / Q.read() * 1.0e-6;
    }

    private static double measureMonteCarlo(Random R) {
        Stopwatch Q = new Stopwatch();

        int cycles = 1;
        while (true) {
            Q.start();
            MonteCarlo.integrate(cycles);
            Q.stop();
            if (Q.read() >= MIN_TIME) break;

            cycles *= 2;
        }
        // approx Mflops
        return MonteCarlo.num_flops(cycles) / Q.read() * 1.0e-6;
    }


    private static double measureSparseMat(int N, int nz, Random R) {
        // initialize vector multipliers and storage for result
        // y = A*y;

        double x[] = RandomVector(N, R);
        double y[] = new double[N];

        // initialize square sparse matrix
        //
        // for this test, we create a sparse matrix wit M/nz nonzeros
        // per row, with spaced-out evenly between the begining of the
        // row to the main diagonal.  Thus, the resulting pattern looks
        // like
        //             +-----------------+
        //             +*                +
        //             +***              +
        //             +* * *            +
        //             +** *  *          +
        //             +**  *   *        +
        //             +* *   *   *      +
        //             +*  *   *    *    +
        //             +*   *    *    *  +
        //             +-----------------+
        //
        // (as best reproducible with integer artihmetic)
        // Note that the first nr rows will have elements past
        // the diagonal.

        int nr = nz / N;        // average number of nonzeros per row
        int anz = nr * N;   // _actual_ number of nonzeros

        double val[] = RandomVector(anz, R);
        int col[] = new int[anz];
        int row[] = new int[N + 1];

        row[0] = 0;
        int step;
        int cycles = 1;

        for (int r = 0; r < N; r++) {
            int rowr = row[r];
            row[r + 1] = rowr + nr;
            step = r / nr;
            if (step < 1) step = 1;   // take at least unit steps

            for (int i = 0; i < nr; i++)
                col[rowr + i] = i * step;
        }

        Stopwatch Q = new Stopwatch();

        while (true) {
            Q.start();
            SparseCompRow.matmult(y, val, row, col, x, cycles);
            Q.stop();
            if (Q.read() >= MIN_TIME) break;

            cycles *= 2;
        }

        // approx Mflops
        return SparseCompRow.num_flops(N, nz, cycles) / Q.read() * 1.0e-6;
    }


    private static double measureLU(int N, Random R) {
        // compute approx Mlfops, or O if LU yields large errors

        double A[][] = RandomMatrix(N, N, R);
        double lu[][] = new double[N][N];
        int pivot[] = new int[N];

        Stopwatch Q = new Stopwatch();

        int cycles = 1;
        while (true) {
            Q.start();
            for (int i = 0; i < cycles; i++) {
                CopyMatrix(lu, A);
                LU.factor(lu, pivot);
            }
            Q.stop();
            if (Q.read() >= MIN_TIME) break;

            cycles *= 2;
        }

        // verify that LU is correct
        double b[] = RandomVector(N, R);
        double x[] = NewVectorCopy(b);

        LU.solve(lu, pivot, x);

        final double EPS = 1.0e-12;
        if (normabs(b, matvec(A, x)) / N > EPS)
            return 0.0;


        // else return approx Mflops
        //
        return LU.num_flops(N) * cycles / Q.read() * 1.0e-6;
    }

    private static double[] NewVectorCopy(double x[]) {
        int N = x.length;

        double y[] = new double[N];
        for (int i = 0; i < N; i++)
            y[i] = x[i];

        return y;
    }

    private static void CopyVector(double B[], double A[]) {
        int N = A.length;

        for (int i = 0; i < N; i++)
            B[i] = A[i];
    }


    private static double normabs(double x[], double y[]) {
        int N = x.length;
        double sum = 0.0;

        for (int i = 0; i < N; i++)
            sum += Math.abs(x[i] - y[i]);

        return sum;
    }

    private static void CopyMatrix(double B[][], double A[][]) {
        int M = A.length;
        int N = A[0].length;

        int remainder = N & 3;         // N mod 4;

        for (int i = 0; i < M; i++) {
            double Bi[] = B[i];
            double Ai[] = A[i];
            for (int j = 0; j < remainder; j++)
                Bi[j] = Ai[j];
            for (int j = remainder; j < N; j += 4) {
                Bi[j] = Ai[j];
                Bi[j + 1] = Ai[j + 1];
                Bi[j + 2] = Ai[j + 2];
                Bi[j + 3] = Ai[j + 3];
            }
        }
    }

    private static double[][] RandomMatrix(int M, int N, Random R) {
        double A[][] = new double[M][N];

        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++)
                A[i][j] = R.nextDouble();
        return A;
    }

    private static double[] RandomVector(int N, Random R) {
        double A[] = new double[N];

        for (int i = 0; i < N; i++)
            A[i] = R.nextDouble();
        return A;
    }

    private static double[] matvec(double A[][], double x[]) {
        int N = x.length;
        double y[] = new double[N];

        matvec(A, x, y);

        return y;
    }

    private static void matvec(double A[][], double x[], double y[]) {
        int M = A.length;
        int N = A[0].length;

        for (int i = 0; i < M; i++) {
            double sum = 0.0;
            double Ai[] = A[i];
            for (int j = 0; j < N; j++)
                sum += Ai[j] * x[j];

            y[i] = sum;
        }
    }
}

