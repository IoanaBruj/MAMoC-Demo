package uk.ac.standrews.cs.mamoc_demo;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;

import uk.ac.standrews.cs.mamoc_client.MamocFramework;
import uk.ac.standrews.cs.mamoc_client.ServiceDiscovery.DiscoveryActivity;
import uk.ac.standrews.cs.mamoc_demo.Benchmark.BenchmarkActivity;
import uk.ac.standrews.cs.mamoc_demo.Fibonacci.FibonacciActivity;
import uk.ac.standrews.cs.mamoc_demo.NQueens.NQueensActivity;
import uk.ac.standrews.cs.mamoc_demo.SearchText.SearchActivity;
import uk.ac.standrews.cs.mamoc_demo.Sorting.SortingActivity;

public class MainActivity extends AppCompatActivity {

    private Button discoveryBtn;
    private Button searchTextDemo, sortingDemo, nqueensDemo, mandelbrotDemo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        discoveryBtn = findViewById(R.id.buttonDiscovery);
        discoveryBtn.setOnClickListener(view -> openDiscoveryActivity());

        searchTextDemo = findViewById(R.id.buttonSearchText);
        searchTextDemo.setOnClickListener(view -> openSearchTextDemo());

        sortingDemo = findViewById(R.id.sortingButton);
        sortingDemo.setOnClickListener(view -> openSortingDemo());

        nqueensDemo = findViewById(R.id.nQueensButton);
        nqueensDemo.setOnClickListener(view -> openNQueensDemo());

        mandelbrotDemo = findViewById(R.id.mandelbrotButton);
        mandelbrotDemo.setOnClickListener(view -> openFibDemo());
    }

    private void openDiscoveryActivity() {
        Intent discoveryIntent = new Intent(this, DiscoveryActivity.class);
        startActivity(discoveryIntent);
    }

    private void openSearchTextDemo() {
        Intent searchIntent = new Intent(this, SearchActivity.class);
        startActivity(searchIntent);
    }

    private void openSortingDemo() {
        Intent sortIntent = new Intent(this, SortingActivity.class);
        startActivity(sortIntent);
    }

    private void openNQueensDemo() {
        Intent nqueensIntent = new Intent(this, NQueensActivity.class);
        startActivity(nqueensIntent);
    }

    private void openFibDemo(){
        Intent FibIntent = new Intent(this, FibonacciActivity.class);
        startActivity(FibIntent);
    }

}
