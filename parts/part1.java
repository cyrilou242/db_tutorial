package org.cyrilou242.homemadedb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;

public class Main {

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        while (true) {
            printPrompt();
            String input = readInput();
            Objects.requireNonNull(input);

            if (input.isEmpty()) {
                continue; // do nothing and loop
            }
            if (input.equals(".exit")) {
                System.out.println("Exiting - Good bye.");
                System.exit(0);
            } else {
                System.out.printf("Unrecognized command: '%s'%n", input);
            }
        }
    }

    private void printPrompt() {
        System.out.print("homemadeDB > ");
    }

    private String readInput() {
        String inputLine = null;
        try {
            BufferedReader is = new BufferedReader(new InputStreamReader(System.in));
            inputLine = is.readLine();
        } catch (IOException e) {
            System.out.println("IOException: " + e);
        }
        return inputLine;
    }
}