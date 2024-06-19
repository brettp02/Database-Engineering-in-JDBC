/*
 * LibraryModel.java
 * Author:
 * Created on:
 */



import javax.swing.*;
import java.sql.*;

import static java.lang.System.exit;

public class LibraryModel {

    // For use in creating dialogs and making them modal
    private JFrame dialogParent;
    private Connection con = null;


    /**
     * Constructor, initialise connection to <id>_jdbc
     * @param parent
     * @param userid
     * @param password
     */
    public LibraryModel(JFrame parent, String userid, String password) {
	    dialogParent = parent;
        String url = "jdbc:postgresql://db.ecs.vuw.ac.nz/" + userid + "_jdbc";

        // Attempt connection to database with given userid and password, if fails it displays the error and throws runtime exception
        try{
            Class.forName("org.postgresql.Driver");
            this.con = DriverManager.getConnection(url,userid,password);
            JOptionPane.showMessageDialog(dialogParent, "Database connection established.");
        }
        catch (SQLException e) {
            String errorMessage = e.getMessage();
            if (errorMessage.contains("password authentication failed")) {
                JOptionPane.showMessageDialog(dialogParent, "Login failed: Password authentication failed", "Login Error", JOptionPane.ERROR_MESSAGE);
            } else if (errorMessage.contains("GSS Authentication failed")) {
                JOptionPane.showMessageDialog(dialogParent, "Login failed: GSS Authentication failed", "Login Error", JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(dialogParent, "Failed to connect to the database: " + e.getMessage(), "Database Connection Error", JOptionPane.ERROR_MESSAGE);
            }
            // Exit the application after any login failure
            exitProgram();
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(dialogParent, "Failed to load the PostgreSQL JDBC driver: " + e.getMessage(), "Driver Error", JOptionPane.ERROR_MESSAGE);
            // Exit the application after any login failure
            exitProgram();
        };

    }


    /**
     * Finds a book based on isbn and returns the related book authors ordered by authseqno
     *
     * @param isbn - isbn of book
     * @return result - String of the result of SQL Query
     */
    public String bookLookup(int isbn) {
        // Construct SQL Quesry
        String query = "SELECT ba.*, a.name, a.surname,b.* " +
                "FROM book_author ba " +
                "JOIN public.author a ON a.authorid = ba.authorid " +
                "JOIN book b ON ba.isbn = b.isbn " +
                "WHERE b.isbn = ? " +
                "ORDER BY ba.authorseqno";

        //use Stringbuilder for result
        StringBuilder result = new StringBuilder();

        try (PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setInt(1, isbn);  // Sets int value for isbn, type safety
            try (ResultSet rs = stmt.executeQuery()) {  //Execture query
                boolean bookFound = false;
                while (rs.next()) { // Iterate over each row in the result set
                    if (!bookFound) {
                        result.append("ISBN: ")
                                .append(isbn)
                                .append("\nBook Lookup:\n")
                                .append("\t" + isbn + ": " + rs.getString("title") + "\n")
                                .append("\tEdition: " + rs.getString("edition_no") + " - Number of copies: " +  rs.getString("numofcop") + " - Copies left: " + rs.getString("numleft"))
                                .append("\n\tAuthors:\n");
                        bookFound = true;
                    }

                    result.append("\t  - ").append(rs.getString("name"))
                            .append(" ").append(rs.getString("surname"))
                            .append(" (AuthorSeqNo: ")
                            .append(rs.getInt("authorseqno"))
                            .append(")\n");
                }
                if (!bookFound) {
                    result.append("No book found with ISBN: ").append(isbn);
                }
            }
        } catch (SQLException e) {
            return "Error executing query: " + e.getMessage();
        }
        return result.toString();
    }


    /**
     * Displays the catlogue (all the books)
     *
     * @return result - Fromatted string with all books/authors for that book
     */
    public String showCatalogue() {

        String query = "SELECT DISTINCT b.*, a.name, a.surname, ba.authorseqno " +
                "FROM book b " +
                "LEFT JOIN book_author ba ON b.isbn = ba.isbn " +
                "LEFT JOIN author a ON a.authorid = ba.authorid " +
                "ORDER BY b.isbn, ba.authorseqno";

        StringBuilder result = new StringBuilder();

        try (PreparedStatement stmt = con.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            int currentIsbn = -1;
            boolean firstRow = true;

            while (rs.next()) {
                int isbn = rs.getInt("isbn");
                if (isbn != currentIsbn) {
                    if (!firstRow) {
                        result.append("\n");
                    }
                    result.append(isbn).append(": ").append(rs.getString("title")).append("\n");
                    result.append("\tEdition: ").append(rs.getInt("edition_no"))
                            .append(" - Number of copies: ").append(rs.getInt("numofcop"))
                            .append(" - Copies left: ").append(rs.getInt("numleft")).append("\n");

                    if (rs.getString("name") == null) {
                        result.append("\t(no authors)\n");
                    } else {
                        result.append("\tAuthor: ").append(rs.getString("name")).append(" ").append(rs.getString("surname")).append("\n");
                    }

                    currentIsbn = isbn;
                    firstRow = false;
                } else {
                    result.append("\tAuthor: ").append(rs.getString("name")).append(" ").append(rs.getString("surname")).append("\n");
                }
            }
        } catch (SQLException e) {
            return "Error executing query: " + e.getMessage();
        }
        return result.toString();

    }

    /**
     * Shows the loaned books
     *
     * @return result, loaned books
     */
    public String showLoanedBooks() {
        String result = "Show Loaned Books:\n";

        String query = "SELECT cb.isbn, cb.customerid, b.title, b.edition_no, b.numofcop, b.numleft, " +
                "string_agg(a.name || ' ' || a.surname, ', ') AS authors " +
                "FROM cust_book cb " +
                "JOIN book b ON cb.isbn = b.isbn " +
                "JOIN book_author ba ON cb.isbn = ba.isbn " +
                "JOIN author a ON ba.authorid = a.authorid " +
                "GROUP BY cb.isbn, cb.customerid, b.title, b.edition_no, b.numofcop, b.numleft " +
                "ORDER BY cb.isbn";

        try (Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            boolean anyBooks = false;

            while (rs.next()) {
                anyBooks = true;
                int isbn = rs.getInt("isbn");
                int customerID = rs.getInt("customerid");
                String title = rs.getString("title");
                int editionNo = rs.getInt("edition_no");
                int numOfCop = rs.getInt("numofcop");
                int numLeft = rs.getInt("numleft");
                String authors = rs.getString("authors");

                result += isbn + ": " + title + "\n";
                result += "    Edition: " + editionNo + " - Number of copies: " + numOfCop + " - Copies left: " + numLeft + "\n";
                result += "    Authors: " + authors + "\n";
                result += "    Borrowers:\n";

                String borrowerQuery = "SELECT cb.customerid, c.l_name, c.f_name, c.city " +
                        "FROM cust_book cb " +
                        "JOIN customer c ON cb.customerid = c.customerid " +
                        "WHERE cb.isbn = " + isbn + " " +
                        "ORDER BY cb.customerid";

                try (Statement borrowerStmt = con.createStatement(); ResultSet rsBorrower = borrowerStmt.executeQuery(borrowerQuery)) {
                    while (rsBorrower.next()) {
                        int borrowerID = rsBorrower.getInt("customerid");
                        String lName = rsBorrower.getString("l_name");
                        String fName = rsBorrower.getString("f_name");
                        String city = rsBorrower.getString("city");
                        result += "        " + borrowerID + ": " + lName + ", " + fName + " - " + city + "\n";
                    }
                }
            }

            if (!anyBooks) {
                result += "(No Loaned Books)";
            }
        } catch (SQLException e) {
            result = "Error loading loaned books: " + e.getMessage();
        }

        return result;
    }


    /**
     * Shows the author/the authors published books, based on input of authorID
     *
     * @param authorID
     * @return - result
     */
    public String showAuthor(int authorID) {
        String result = "";

        String query = "SELECT a.authorid, a.name, a.surname, b.isbn, b.title " +
                "FROM author a " +
                "LEFT JOIN book_author ba ON a.authorid = ba.authorid " +
                "LEFT JOIN book b ON ba.isbn = b.isbn " +
                "WHERE a.authorid = ? " +
                "ORDER BY ba.isbn";

        try (PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setInt(1, authorID);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    result = "Show Author:\n    No such author ID: " + authorID;
                } else {
                    result = "Show Author:\n";
                    boolean firstRow = true;
                    String authorName = "";

                    do {
                        if (firstRow) {
                            authorName = rs.getString("name") + " " + rs.getString("surname");
                            result += "    " + authorID + " - " + authorName + "\n    Book written:\n";
                            firstRow = false;
                        }
                        int isbn = rs.getInt("isbn");
                        String title = rs.getString("title");

                        if (title != null) {
                            result += "        " + isbn + " - " + title + "\n";
                        }
                    } while (rs.next());
                }
            }
        } catch (SQLException e) {
            result = "Error showing author: " + e.getMessage();
        }

        return result;
    }


    /**
     * Simple method to print all authors in order from lowest to highest authorID
     *
     * @return result
     */
    public String showAllAuthors() {
	    StringBuilder result = new StringBuilder("Show All Authors:\n");

        String query =  "SELECT DISTINCT a.authorid, a.name, a.surname " +
                        "FROM author a " +
                        "ORDER BY a.authorid;";

        try (Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(query)){
            while (rs.next()) {
                int count = rs.getInt("authorid");
                String authorName = rs.getString("surname") + ", " + rs.getString("name");
                result.append("    ").append(count).append(": ").append(authorName).append("\n");
            }

        } catch (SQLException e){
            return "Error showing all authors" + e.getMessage();
        }

        return result.toString();
    }


    /**
     * Shows the customer, is not handling non borrowers correctly, don'thave enough time to fix
     *
     * @param - customerid
     * @return - result
     */
    public String showCustomer(int customerID) {
	    String result = "";

        String query =  "SELECT c.customerid, c.f_name, c.l_name, c.city, cb.isbn, b.title " +
                        "FROM customer c " +
                        "JOIN cust_book cb ON c.customerid = cb.customerid " +
                        "JOIN book b ON cb.isbn = b.isbn " +
                        "WHERE c.customerid = ? "+
                        "ORDER BY b.isbn DESC; ";

        boolean hasBorrowed = true;

        try (PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setInt(1, customerID);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("!rs.next");
                } else {
                    result = "Show Customer:\n";
                    boolean firstRow = true;
                    String custName = "";

                    do {
                        if (firstRow) {
                            custName = rs.getString("f_name") + " " + rs.getString("l_name");
                            result += "    " + customerID + ": " + custName + "\n    Book Borrowed:\n";
                            firstRow = false;
                        }

                        int isbn = rs.getInt("isbn");
                        String title = rs.getString("title");

                        if (title != null) {
                            result += "        " + isbn + " - " + title + "\n";
                        }
                        else {
                            result += "\t (No books borrowed)";
                        }
                    } while (rs.next());
                }
            }
        } catch (SQLException e) {
            result = "Error showing customer: " + e.getMessage();
        }

        return result;
    }


    /**
     * Shows all customers and their cID + city
     *
     * @return - result
     */
    public String showAllCustomers() {
	    StringBuilder result = new StringBuilder("Show all Customers:\n");
        String query =  "SELECT DISTINCT c.customerid, c.f_name, c.l_name, c.city " +
                        "FROM customer c " +
                        "ORDER BY c.customerid;";

        try (Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(query)){
            while (rs.next()) {
                int count = rs.getInt("customerid");
                String custName = rs.getString("l_name") + ", " + rs.getString("f_name");

                String city = "(no city)";

                if(rs.getString("city") != null){
                    city = rs.getString("city");
                }

                result.append("    ").append(count).append(": ").append(custName).append(" - " + city).append("\n");
            }

        } catch (SQLException e){
            return "Error showing all customers" + e.getMessage();
        }

        return result.toString();

    }


    /**
     * Allows customers to borrow books, aslong as it is stock, with a return date
     * Opposite of borrow book
     *
     * @param day
     * @param isbn
     * @param month
     * @param year
     * @param customerID
     *
     * @return result
     */
    public String borrowBook(int isbn, int customerID,
			     int day, int month, int year) {
        String result;
        String checkCustomerQuery = "SELECT * FROM Customer WHERE CustomerID = ? FOR UPDATE";
        String checkBookQuery = "SELECT * FROM Book WHERE ISBN = ? AND NumLeft > 0 FOR UPDATE";
        String insertCustBookQuery = "INSERT INTO Cust_Book (CustomerID, ISBN, DueDate) VALUES (?, ?, ?)";
        String updateBookQuery = "UPDATE Book SET NumLeft = NumLeft - 1 WHERE ISBN = ?";
        String getCustomerInfoQuery = "SELECT * FROM Customer WHERE CustomerID = ?";
        String getBookInfoQuery = "SELECT * FROM Book WHERE ISBN = ?";

        try {
            con.setAutoCommit(false);

            // Check if the customer exists and lock the customer row
            try (PreparedStatement checkCustomerStmt = con.prepareStatement(checkCustomerQuery)) {
                checkCustomerStmt.setInt(1, customerID);
                try (ResultSet rsCustomer = checkCustomerStmt.executeQuery()) {
                    if (!rsCustomer.next()) {
                        throw new SQLException("Customer does not exist.");
                    }
                }
            }

            // Check if the book exists and a copy is available, and lock the book row
            try (PreparedStatement checkBookStmt = con.prepareStatement(checkBookQuery)) {
                checkBookStmt.setInt(1, isbn);
                try (ResultSet rsBook = checkBookStmt.executeQuery()) {
                    if (!rsBook.next()) {
                        throw new SQLException("Book is not available.");
                    }
                }
            }

            JOptionPane.showMessageDialog(dialogParent, "Locked the tuple(s), ready to update.\n Click OK to continue");

            // Insert a tuple into the Cust_Book table
            try (PreparedStatement insertCustBookStmt = con.prepareStatement(insertCustBookQuery)) {
                insertCustBookStmt.setInt(1, customerID);
                insertCustBookStmt.setInt(2, isbn);
                insertCustBookStmt.setDate(3, java.sql.Date.valueOf(String.format("%d-%02d-%02d", year, month + 1, day)));
                insertCustBookStmt.executeUpdate();
            }

            // Update the Book table to decrease the number of copies left
            try (PreparedStatement updateBookStmt = con.prepareStatement(updateBookQuery)) {
                updateBookStmt.setInt(1, isbn);
                updateBookStmt.executeUpdate();
            }

            con.commit();

            String customerName = "";
            String bookTitle = "";

            try (PreparedStatement getCustomerInfoStmt = con.prepareStatement(getCustomerInfoQuery)) {
                getCustomerInfoStmt.setInt(1, customerID);
                try (ResultSet rsCustomer = getCustomerInfoStmt.executeQuery()) {
                    if (rsCustomer.next()) {
                        customerName = rsCustomer.getString("L_Name") + " " + rsCustomer.getString("F_Name");
                    }
                }
            }

            try (PreparedStatement getBookInfoStmt = con.prepareStatement(getBookInfoQuery)) {
                getBookInfoStmt.setInt(1, isbn);
                try (ResultSet rsBook = getBookInfoStmt.executeQuery()) {
                    if (rsBook.next()) {
                        bookTitle = rsBook.getString("Title");
                    }
                }
            }

            // Format the due date
            java.text.DateFormat dateFormat = new java.text.SimpleDateFormat("dd MMM yyyy");
            java.util.Date dueDate = new java.sql.Date(java.sql.Date.valueOf(String.format("%d-%02d-%02d", year, month + 1, day)).getTime());
            String formattedDueDate = dateFormat.format(dueDate);


            result = "Borrow Book:\n" +
                    "    Book: " + isbn + " (" + bookTitle + ")\n" +
                    "    Loaned to: " + customerID + " (" + customerName + ")\n" +
                    "    Due Date: " + formattedDueDate;

        } catch (SQLException e) {
            try {
                con.rollback();
            } catch (SQLException rollbackEx) {
                result = "Error during rollback: " + rollbackEx.getMessage();
            }
            result = "Error borrowing book: " + e.getMessage();
        } finally {
            try {
                con.setAutoCommit(true);
            } catch (SQLException e) {
                result = "Error restoring auto-commit mode: " + e.getMessage();
            }
        }

        return result;
    }


    /**
     * Allows customer to return book
     *
     * @param isbn
     * @param customerid
     * @return result
     */
    public String returnBook(int isbn, int customerid) {
        String result;
        String deleteFromCustBookQuery = "DELETE FROM Cust_Book WHERE CustomerID = ? AND ISBN = ?";
        String updateBookQuery = "UPDATE Book SET NumLeft = NumLeft + 1 WHERE ISBN = ?";
        String getCustomerInfoQuery = "SELECT * FROM Customer WHERE CustomerID = ?";
        String getBookInfoQuery = "SELECT * FROM Book WHERE ISBN = ?";

        try {
            con.setAutoCommit(false);

            // Delete the entry from the Cust_Book table
            try (PreparedStatement deleteCustBookStmt = con.prepareStatement(deleteFromCustBookQuery)) {
                deleteCustBookStmt.setInt(1, customerid);
                deleteCustBookStmt.setInt(2, isbn);
                int rowsDeleted = deleteCustBookStmt.executeUpdate();
                if (rowsDeleted == 0) {
                    throw new SQLException("No entry found in Cust_Book for the given customer and book.");
                }
            }

            JOptionPane.showMessageDialog(dialogParent, "Locked the tuple(s), ready to update.\n Click OK to continue");

            // Update the Book table to increase the number of copies left
            try (PreparedStatement updateBookStmt = con.prepareStatement(updateBookQuery)) {
                updateBookStmt.setInt(1, isbn);
                updateBookStmt.executeUpdate();
            }

            con.commit();

            // Retrieve customer and book information for the final output
            String customerName = "";
            String bookTitle = "";

            try (PreparedStatement getCustomerInfoStmt = con.prepareStatement(getCustomerInfoQuery)) {
                getCustomerInfoStmt.setInt(1, customerid);
                try (ResultSet rsCustomer = getCustomerInfoStmt.executeQuery()) {
                    if (rsCustomer.next()) {
                        customerName = rsCustomer.getString("L_Name") + " " + rsCustomer.getString("F_Name");
                    }
                }
            }

            try (PreparedStatement getBookInfoStmt = con.prepareStatement(getBookInfoQuery)) {
                getBookInfoStmt.setInt(1, isbn);
                try (ResultSet rsBook = getBookInfoStmt.executeQuery()) {
                    if (rsBook.next()) {
                        bookTitle = rsBook.getString("Title");
                    }
                }
            }

            result = "Return Book:\n" +
                    "    Book: " + isbn + " (" + bookTitle + ")\n" +
                    "    Returned by: " + customerid + " (" + customerName + ")";

        } catch (SQLException e) {
            try {
                con.rollback();
            } catch (SQLException rollbackEx) {
                result = "Error during rollback: " + rollbackEx.getMessage();
            }
            result = "Error returning book: " + e.getMessage();
        } finally {
            try {
                con.setAutoCommit(true);
            } catch (SQLException e) {
                result = "Error restoring auto-commit mode: " + e.getMessage();
            }
        }

        return result;

    }

    // Exit program does the job
    public void closeDBConnection() {
        //this.closeDBConnection();
        //System.exit(0);
    }


    /**
     * Gives the option to exit out application if you press OK
     */
    public void exitProgram() {
        int result = JOptionPane.showOptionDialog(dialogParent, "Click 'Ok' to exit.", "Exit Application", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE, null, null, null);

        if (result == JOptionPane.OK_OPTION) {
            this.closeDBConnection();
            System.exit(0);
        }
    }


    /**
     * Delete a customer based on customerID
     *
     * @param customerID
     * @return result
     */
    public String deleteCus(int customerID) {
        String result = "";
        String query = "DELETE FROM customer WHERE customerid = ?";

        try (PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setInt(1, customerID);
            int rowsDeleted = stmt.executeUpdate();

            if (rowsDeleted > 0) {
                result = "Customer: " + customerID + " removed";
            } else {
                result = "No customer found with ID: " + customerID;
            }
        } catch (SQLException e) {
            result = "Error deleting customer: " + e.getMessage();
        }

        return result;
    }


    /**
     * Delete an author based on AuthorID
     *
     * @param authorID
     * @return result
     */
    public String deleteAuthor(int authorID) {
        String result = "";
        String query = "DELETE FROM author WHERE authorid = ?";

        try (PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setInt(1, authorID);
            int rowsDeleted = stmt.executeUpdate();

            if (rowsDeleted > 0) {
                result = "Author: " + authorID + " removed";
            } else {
                result = "No Author found with ID: " + authorID;
            }
        } catch (SQLException e) {
            result = "Error deleting Author: " + e.getMessage();
        }

        return result;
    }


    /**
     * Delete a book based on isbn
     *
     * @param isbn
     * @return result
     */
    public String deleteBook(int isbn) {
        String result = "";
        String query = "DELETE FROM book WHERE isbn = ?";

        try (PreparedStatement stmt = con.prepareStatement(query)) {
            stmt.setInt(1, isbn);
            int rowsDeleted = stmt.executeUpdate();

            if (rowsDeleted > 0) {
                result = "Book: " + isbn + " removed";
            } else {
                result = "No Book found with isbn: " + isbn;
            }
        } catch (SQLException e) {
            result = "Error deleting Book: " + e.getMessage();
        }

        return result;
    }
}