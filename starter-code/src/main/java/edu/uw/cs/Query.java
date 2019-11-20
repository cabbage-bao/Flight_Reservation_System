package edu.uw.cs;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.xml.*;

/**
 * Runs queries against a back-end database
 */
public class Query {
  // DB Connection
  private Connection conn;

  // Password hashing parameter constants
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;

  //private field to indicate whether users have logged in
  private String loggedUser;
  private int rid = 1;
  private List<Itinerary> iList = new ArrayList<>();

  // Canned queries
  private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement checkFlightCapacityStatement;

  private static final String GET_USER = "SELECT * FROM Users where username = ? and password = ?;";
  private PreparedStatement getUser;

  private static final String SEARCH_DIRECT = "SELECT TOP (?) * FROM Flights "
          + "WHERE day_of_month = ? AND origin_city = ? AND dest_city =  ? "
          + "AND canceled <> 1 ORDER BY actual_time ASC, fid ASC;";
  private PreparedStatement searchDirect;

  private static final String SEARCH_INDIRECT = "select top(?) * from FLIGHTS F1, FLIGHTS F2 " +
          "where F1.day_of_month = ? AND F2.day_of_month = ? AND F1.origin_city = ? AND F1.dest_city = F2.origin_city "
          + "AND F2.dest_city = ? AND F1.canceled != 1 AND F2.canceled != 1 ORDER BY (F1.actual_time+F2.actual_time), F1.fid ASC;";
  private PreparedStatement searchIndirect;

  private static final String SEARCH_RESERVATION = "SELECT * FROM Reservation WHERE username = ? ORDER BY rid ASC";
  private PreparedStatement searchReservation;

  private static final String GET_CAPACITIES = "SELECT capacity FROM CAPACITIES WHERE fid = ?;";
  private PreparedStatement getCapacities;

  private static final String FIND_BALANCE = "select balance from users where username = ?;";
  private PreparedStatement findBalance;

  private static final String GET_PAID_STATUS = "select * from reservation where rid = ?;";
  private PreparedStatement getReservationByRid;

  private static final String GET_FLIGHT = "select * from flights where fid = ?;";
  private PreparedStatement getFlight;



  //   All update operation   *********************************************************************************
  private static final String CLEAR_USER_TABLE = "DELETE FROM Users;";
  private PreparedStatement clearTableUsers;

  private static final String CLEAR_RESERVATION_TABLE = "DELETE FROM Reservation;";
  private PreparedStatement clearTableReservations;

  private static final String CLEAR_CAPACITY_TABLE = "DELETE FROM Capacities;";
  private PreparedStatement clearTableCapacities;

  private static final String CREATE_USER = "INSERT INTO Users VALUES(?, ?, ?)";
  private PreparedStatement createCustomer;

  private  static final String UPDATE_CAPACITY = "UPDATE Capacities set capacity = ((select capacity from capacities where fid = ?) - 1) where fid = ?;";
  private PreparedStatement updateCapacity;

  private static final String INSERT_CAPACITY = "INSERT INTO CAPACITIES "
          + "SELECT F.fid, F.capacity "
          + "FROM Flights F "
          + "WHERE f.fid = ? "
          + "AND NOT EXISTS "
          + "(SELECT * FROM CAPACITIES c WHERE c.fid = f.fid);";
  private PreparedStatement insertCapacity;

  private static final String UPDATE_RESERVATION = "INSERT INTO RESERVATION (rid, fid1, fid2, paid, username, cost, canceled) " +
          "VALUES(?, ?, ?, ?, ?, ?, ?);";
  private PreparedStatement updateReservation;

  private static final String UPDATE_BALANCE = "UPDATE USERS set balance = ? where username = ?;";
  private PreparedStatement updateBalance;

  private static final String SET_PAID_STATUS = "UPDATE RESERVATION SET paid = ? WHERE rid = ?;";
  private PreparedStatement setPaidStatus;

  private static final String DELETE_RESERVATION = "DELETE FROM reservation where rid = ?;";
  private PreparedStatement deleteReservation;

  private static final String ADD = "UPDATE Capacities SET capacity = ? WHERE fid = ?";
  private PreparedStatement addCapacity;

  // Update paid and canceled
  private static final String CANCEL = "UPDATE Reservation SET paid = ?, canceled = ? WHERE rid = ?";
  private PreparedStatement cancelReservation;



  /**
   * Establishes a new application-to-database connection. Uses the
   * dbconn.properties configuration settings
   *
   * @throws IOException
   * @throws SQLException
   */
  public void openConnection() throws IOException, SQLException {
    // Connect to the database with the provided connection configuration
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));
    String serverURL = configProps.getProperty("hw1.server_url");
    String dbName = configProps.getProperty("hw1.database_name");
    String adminName = configProps.getProperty("hw1.username");
    String password = configProps.getProperty("hw1.password");
    String connectionUrl = String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
            dbName, adminName, password);
    conn = DriverManager.getConnection(connectionUrl);

    // By default, automatically commit after each statement
    conn.setAutoCommit(true);

    // By default, set the transaction isolation level to serializable
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
  }

  /**
   * Closes the application-to-database connection
   */
  public void closeConnection() throws SQLException {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created.
   *
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      clearTableReservations.executeUpdate();
      clearTableUsers.executeUpdate();
      clearTableCapacities.executeUpdate();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  public void prepareStatements() throws SQLException {
    checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);

    clearTableUsers = conn.prepareStatement(CLEAR_USER_TABLE);
    clearTableReservations = conn.prepareStatement(CLEAR_RESERVATION_TABLE);
    clearTableCapacities = conn.prepareStatement(CLEAR_CAPACITY_TABLE);
    getUser = conn.prepareStatement(GET_USER);
    createCustomer = conn.prepareStatement(CREATE_USER);
    searchDirect = conn.prepareStatement(SEARCH_DIRECT);
    searchIndirect = conn.prepareStatement(SEARCH_INDIRECT);
    searchReservation = conn.prepareStatement(SEARCH_RESERVATION);
    insertCapacity = conn.prepareStatement(INSERT_CAPACITY);
    getCapacities = conn.prepareStatement(GET_CAPACITIES);
    updateCapacity = conn.prepareStatement(UPDATE_CAPACITY);
    updateReservation = conn.prepareStatement(UPDATE_RESERVATION);
    //countReservation = conn.prepareStatement(COUNT_RESERVATION);
    findBalance = conn.prepareStatement(FIND_BALANCE);
    getReservationByRid = conn.prepareStatement(GET_PAID_STATUS);
    updateBalance = conn.prepareStatement(UPDATE_BALANCE);
    setPaidStatus = conn.prepareStatement(SET_PAID_STATUS);
    getFlight = conn.prepareStatement(GET_FLIGHT);
    //deleteReservation = conn.prepareStatement(DELETE_RESERVATION);

    cancelReservation = conn.prepareStatement(CANCEL);
    addCapacity = conn.prepareStatement(ADD);

  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged
   *         in\n" For all other errors, return "Login failed\n". Otherwise,
   *         return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password){

    if (this.loggedUser != null){
      return "User already logged in\n";
    }
    try{
      getUser.clearParameters();
      getUser.setString(1, username);
      getUser.setString(2, password);
      ResultSet user = getUser.executeQuery();
      if (user.next()){
        this.loggedUser = username;
        user.close();
        return "Logged in as " + loggedUser + "\n";
      }
      user.close();
    }catch (SQLException e){
      e.printStackTrace();
    }
    return "Login failed\n";
  }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should
   *                   be >= 0 (failure otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n"
   *         if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount){

    //PreparedStatement createCustomer = conn.prepareStatement("insert into Users values(?, ?, ?)");
    try{
      if (initAmount >= 0){
        getUser.clearParameters();
        getUser.setString(1, username);
        getUser.setString(2, password);
        ResultSet result = getUser.executeQuery();
        if (result.next()){
          result.close();
          return "Failed to create user, user alreay exists\n";
        }
        createCustomer.clearParameters();
        createCustomer.setString(1, username);
        createCustomer.setString(2, password);
        createCustomer.setInt(3, initAmount);

        createCustomer.executeUpdate();
        return "Created user " + username + "\n";
      }
    } catch (SQLException e){
      e.printStackTrace();
    }

    return "Failed to create user\n";
  }

  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination
   * city, on the given day of the month. If {@code directFlight} is true, it only
   * searches for direct flights, otherwise is searches for direct flights and
   * flights with two "hops." Only searches for up to the number of itineraries
   * given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights,
   *                            otherwise include indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   *
   * @return If no itineraries were found, return "No flights match your
   *         selection\n". If an error occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total
   *         flight time] minutes\n [first flight in itinerary]\n ... [last flight
   *         in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the
   *         {@code Flight} class. Itinerary numbers in each search should always
   *         start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries){
    StringBuffer sb = new StringBuffer();
    try{
      //iList = new LinkedList<>();
      // one hop itineraries
      searchDirect.clearParameters();
      searchDirect.setInt(1, numberOfItineraries);         //limit num of results within numberOfItineraries
      searchDirect.setInt(2, dayOfMonth);
      searchDirect.setString(3, originCity);
      searchDirect.setString(4, destinationCity);

      ResultSet directFlights = searchDirect.executeQuery();
      //int num = numberOfItineraries;
      int count = 0;
      while (directFlights.next()) {                     // find all the direct flights (less than numberOfItineraries)

        int result_dayOfMonth = directFlights.getInt("day_of_month");
        String result_carrierId = directFlights.getString("carrier_id");
        String result_flightNum = directFlights.getString("flight_num");
        String result_originCity = directFlights.getString("origin_city");
        String result_destCity = directFlights.getString("dest_city");
        int result_time = directFlights.getInt("actual_time");
        int result_capacity = directFlights.getInt("capacity");
        int result_price = directFlights.getInt("price");
        int flight_id = directFlights.getInt("fid");

        Itinerary singleFlight = new Itinerary();                               // Add current itinerary to the list
        singleFlight.fid1 = flight_id;
        singleFlight.cost = result_price;
        singleFlight.day = result_dayOfMonth;
        singleFlight.fid2 = 0;
        iList.add(singleFlight);

        sb.append("Itinerary " + count + ": 1 flight(s), " + result_time + " minutes\n");
        sb.append("ID: " + flight_id + " Day: " + result_dayOfMonth + " Carrier: " + result_carrierId + " Number: " +
                result_flightNum + " Origin: " + result_originCity + " Dest: " + result_destCity + " Duration: " + result_time
                + " Capacity: " + result_capacity + " Price: " + result_price + "\n");
        //num--;
        count++;
      }
      directFlights.close();

      if (directFlight){                                      //If is direct, then return
        return sb.toString();
      }
      else {
        if (count == numberOfItineraries){                   //if not direct but more direct flight than numberOfItineraries
          return sb.toString();                              // output
        }else {
          searchIndirect.clearParameters();
          searchIndirect.setInt(1, numberOfItineraries - count);
          searchIndirect.setInt(2, dayOfMonth);
          searchIndirect.setInt(3, dayOfMonth);
          searchIndirect.setString(4, originCity);
          searchIndirect.setString(5, destinationCity);
          ResultSet indirectFlights = searchIndirect.executeQuery();

          while (indirectFlights.next()){                     //else, search for indirect flights
            int time1 = indirectFlights.getInt(15);
            int time2 = indirectFlights.getInt(33);
            int time = time1 + time2;

            int fid1 = indirectFlights.getInt(1);
            int fid2 = indirectFlights.getInt(19);

            int dayId = indirectFlights.getInt(3);

            String carrier1 = indirectFlights.getString(5);
            String carrier2 = indirectFlights.getString(23);

            int flightNum1 = indirectFlights.getInt(6);
            int flightNum2 = indirectFlights.getInt(24);

            String origin1 = indirectFlights.getString(7);
            String origin2 = indirectFlights.getString(25);

            String dest1 = indirectFlights.getString(9);
            String dest2 = indirectFlights.getString(27);

            int capa1 = indirectFlights.getInt(17);
            int capa2 = indirectFlights.getInt(35);

            int price1 = indirectFlights.getInt(18);
            int price2 = indirectFlights.getInt(36);

            Itinerary twoFlight = new Itinerary();                          // Add current itinerary to the list
            twoFlight.fid1 = fid1;
            twoFlight.fid2 = fid2;
            twoFlight.cost = price1 + price2;
            twoFlight.day = dayId;
            iList.add(twoFlight);

            sb.append("Itinerary " + count + ": 2 flight(s), " + time + "minutes\n");
            sb.append("ID: " + fid1 + "Day: " + dayId + " Carrier: " + carrier1 + " Number: " +
                    flightNum1 + " Origin: " + origin1 + " Dest: " + dest1 + " Duration: " + time1
                    + " Capacity: " + capa1 + " Price: " + price1 + "\n");
            sb.append("ID: " + fid2 + "Day: " + dayId + " Carrier: " + carrier2 + " Number: " +
                    flightNum2 + " Origin: " + origin2 + " Dest: " + dest2 + " Duration: " + time2
                    + " Capacity: " + capa2 + " Price: " + price2 + "\n");
            count++;
          }
        }
        return sb.toString();

      }
    } catch (SQLException e){
      e.printStackTrace();
    }
    return "search failed\n";
  }

  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is
   *                    returned by search in the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations,
   *         not logged in\n". If try to book an itinerary with invalid ID, then
   *         return "No such itinerary {@code itineraryId}\n".
   *         If the user already has a reservation on the same day as the one that they are trying to
   *         book now, then return "You cannot book two flights in the same
   *         day\n". For all other errors, return "Booking failed\n".
   *
   *         And if booking succeeded, return "Booked flight(s), reservation ID:
   *         [reservationId]\n" where reservationId is a unique number in the
   *         reservation system that starts from 1 and increments by 1 each time a
   *         successful reservation is made by any user in the system.
   */
  public String transaction_book(int itineraryId){

    // user not logged in
    if (loggedUser == null){
      return "Cannot book reservations, not logged in\n";
    }

    if (itineraryId < 0 || itineraryId >= iList.size() || iList.size() < 1){
      return "No such itinerary " + itineraryId + "\n";
    }

    Itinerary temp = iList.get(itineraryId);             // temp is the Itinerary that user want to book
    int fidWant1 = temp.fid1;
    int fidWant2 = temp.fid2;
    int cost = temp.cost;
    try{
      searchReservation.clearParameters();
      searchReservation.setString(1, this.loggedUser);
      ResultSet reservationResult = searchReservation.executeQuery();
      while (reservationResult.next()){                    // check if itinerary is already in the reservation
        int fid1 = reservationResult.getInt("fid1");
        int fid2 = reservationResult.getInt("fid2");
        if (fidWant1 == fid1 || fidWant1 == fid2 || fidWant2 == fid1 || fidWant2 == fid2){
          reservationResult.close();
          return "You cannot book two flights in the same day\n";
        }
      }

      //firstly need to check Capacity
      //CAPACITIES table needs to be on hold
      //check curr_capacity
      //if curr_capacity < max_capacity
      //set curr_capacity to curr_capacity + 1
      insertCapacity.clearParameters();
      insertCapacity.setInt(1, fidWant1);
      insertCapacity.executeUpdate();

      getCapacities.clearParameters();
      getCapacities.setInt(1, fidWant1);
      ResultSet getCap1 = getCapacities.executeQuery();
      getCap1.next();
      int cap1 = getCap1.getInt("capacity");
      getCap1.close();

      if (cap1 == 0){
        return "Booking failed!\n";
      }

      if (fidWant2 != 0){                       //check if there is space in the second flight if there is a second flight
        insertCapacity.clearParameters();
        insertCapacity.setInt(1, fidWant2);
        insertCapacity.executeUpdate();

        getCapacities.clearParameters();
        getCapacities.setInt(1, fidWant1);
        ResultSet getCap2 = getCapacities.executeQuery();
        getCap2.next();
        int cap2 = getCap1.getInt("capacity");
        getCap2.close();

        if (cap2 == 0){
          return "Booking failed!\n";
        }
      }

      updateCapacity.clearParameters();             //update capacities of flights
      updateCapacity.setInt(1, fidWant1);
      updateCapacity.setInt(2, fidWant1);
      updateCapacity.executeUpdate();

      if (fidWant2 != 0){
        updateCapacity.clearParameters();
        updateCapacity.setInt(1, fidWant2);
        updateCapacity.setInt(2, fidWant2);
        updateCapacity.executeUpdate();
      }

      updateReservation.clearParameters();          //update reservation
      updateReservation.setInt(1, rid);
      updateReservation.setInt(2, fidWant1);
      if (fidWant2 != 0){
        updateReservation.setInt(3, fidWant2);
      }else {                                                   // if fid2 not exists, then set fid2 to 0
        updateReservation.setInt(3, 0);
      }
      updateReservation.setInt(4, 0);
      updateReservation.setString(5, this.loggedUser);
      updateReservation.setInt(6, cost);
      updateReservation.setInt(7, 0);                          //update canceled
      updateReservation.executeUpdate();
      rid++;
      return "Booked flight(s), reservation ID: " + (rid-1) + "\n";

    } catch(SQLException e){
      e.printStackTrace();
    }
    return "Booking failed!\n";
  }

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n"
   *         If the reservation is not found / not under the logged in user's
   *         name, then return "Cannot find unpaid reservation [reservationId]
   *         under user: [username]\n" If the user does not have enough money in
   *         their account, then return "User has only [balance] in account but
   *         itinerary costs [cost]\n" For all other errors, return "Failed to pay
   *         for reservation [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining
   *         balance: [balance]\n" where [balance] is the remaining balance in the
   *         user's account.
   */
  public String transaction_pay(int reservationId){
    //If no user has logged in, then return "Cannot pay, not logged in\n"
    if (this.loggedUser == null){
      return "Cannot pay, not logged in\n";
    }
    try{
      //If the reservation is not found / not under the logged in user's
      //         name
      getReservationByRid.clearParameters();                  //return reservations with rid
      getReservationByRid.setInt(1, reservationId);
      ResultSet getResult = getReservationByRid.executeQuery();

      if (!getResult.next()){
        return "Cannot find unpaid reservation " + reservationId + " under user: " + this.loggedUser + "\n";
      }
      else {
        int paid = getResult.getInt("paid");
        int canceled = getResult.getInt("canceled");
        if (paid == 1 || canceled == 1){                                    // if already paid or canceled
          getResult.close();
          return "Cannot find unpaid reservation " + reservationId + " under user: " + this.loggedUser + "\n";
        }
      /*  If the user does not have enough money in
        their account, then return "User has only [balance] in account but
        itinerary costs [cost]\n"*/
        int cost = getResult.getInt("cost");

        findBalance.clearParameters();
        findBalance.setString(1, this.loggedUser);
        ResultSet getBalance = findBalance.executeQuery();
        getBalance.next();
        int balance = getBalance.getInt(1);

        if (cost > balance){                            //if cost > balance means no enough money
          getBalance.close();
          getReservationByRid.close();
          return "User has only " + balance + " in account but itinerary costs " + cost + "\n";
        }

        updateBalance.clearParameters();                    // update balance
        updateBalance.setInt(1, balance - cost);
        updateBalance.setString(2, this.loggedUser);
        updateBalance.executeUpdate();

        setPaidStatus.clearParameters();                     //update paid_status
        setPaidStatus.setInt(1, 1);
        setPaidStatus.setInt(2, reservationId);
        setPaidStatus.executeUpdate();
        return "Paid reservation: " + reservationId + " remaining balance: " + (balance - cost) + "\n";
      }
    } catch(SQLException e){
      e.printStackTrace();
    }
    return "Failed to pay for reservation " + reservationId + "\n";
  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not
   *         logged in\n" If the user has no reservations, then return "No
   *         reservations found\n" For all other errors, return "Failed to
   *         retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n" [flight 1
   *         under the reservation] [flight 2 under the reservation] Reservation
   *         [reservation ID] paid: [true or false]:\n" [flight 1 under the
   *         reservation] [flight 2 under the reservation] ...
   *
   *         Each flight should be printed using the same format as in the
   *         {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations(){
    // check if user has logged in
    if (this.loggedUser == null){
      return "Cannot view reservations, not logged in\n";
    }

    try{
      searchReservation.clearParameters();
      searchReservation.setString(1, this.loggedUser);
      ResultSet resResult = searchReservation.executeQuery();
      //ResultSet resResult = resResult2;
      //System.out.println(resResult);
      if (!resResult.isBeforeFirst()) {
        return "No reservations found\n";
      }
      else {
        StringBuffer sb = new StringBuffer();
        while (resResult.next()) {
          int id = resResult.getInt("rid");
          int paid = resResult.getInt("paid");
          int fid1 = resResult.getInt("fid1");
          int fid2 = resResult.getInt("fid2");
          // get fid1 information
          getFlight.clearParameters();
          getFlight.setInt(1, fid1);
          ResultSet fid1Res = getFlight.executeQuery();
          fid1Res.next();

          int dayOfMonth1 = fid1Res.getInt("day_of_month");
          String carrierId1 = fid1Res.getString("carrier_id");
          String flightNum1 = fid1Res.getString("flight_num");
          String originCity1 = fid1Res.getString("origin_city");
          String destCity1 = fid1Res.getString("dest_city");
          int time1 = fid1Res.getInt("actual_time");
          int capacity1 = fid1Res.getInt("capacity");
          int price1 = fid1Res.getInt("price");
          fid1Res.close();
          String ispaid;
          if (paid == 1) {
            ispaid = "true:";
          }
          else {
            ispaid = "false:";
          }
          sb.append("Reservation " + id + " paid: " + ispaid + "\n");
          sb.append("ID: " + fid1 + " Day: " + dayOfMonth1 + " Carrier: " + carrierId1 + " Number: " + flightNum1 + " Origin: " + originCity1
                  + " Dest: " + destCity1 + " Duration: " + time1 + " Capacity: " + capacity1 + " Price: " + price1 + "\n");
          // get fid2 information
          if (fid2 != 0) {
            getFlight.clearParameters();
            getFlight.setInt(1, fid2);
            ResultSet fid2Res = getFlight.executeQuery();
            fid2Res.next();

            int dayOfMonth2 = fid2Res.getInt("day_of_month");
            String carrierId2 = fid2Res.getString("carrier_id");
            String flightNum2 = fid2Res.getString("flight_num");
            String originCity2 = fid2Res.getString("origin_city");
            String destCity2 = fid2Res.getString("dest_city");
            int time2 = fid2Res.getInt("actual_time");
            int capacity2 = fid2Res.getInt("capacity");
            int price2 = fid2Res.getInt("price");
            fid2Res.close();
            sb.append("ID: " + fid2 + " Day: " + dayOfMonth2 + " Carrier: " + carrierId2 + " Number: " + flightNum2 + " Origin: " + originCity2
                    + " Dest: " + destCity2 + " Duration: " + time2 + " Capacity: " + capacity2 + " Price: " + price2 + "\n");
          }
        }
        return sb.toString();
      }
    } catch(SQLException e){
      e.printStackTrace();
    }
    return "Failed to retrieve reservations\n";
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   *
   * @return If no user has logged in, then return "Cannot cancel reservations,
   *         not logged in\n" For all other errors, return "Failed to cancel
   *         reservation [reservationId]\n"
   *
   *         If successful, return "Canceled reservation [reservationId]\n"
   *
   *         Even though a reservation has been canceled, its ID should not be
   *         reused by the system.
   */
  public String transaction_cancel(int reservationId){
    if (this.loggedUser == null){
      return "Cannot cancel reservations, not logged in\n";
    }
    try{
      getReservationByRid.clearParameters();                  //return reservations with rid
      getReservationByRid.setInt(1, reservationId);
      ResultSet getResult = getReservationByRid.executeQuery();

      if (!getResult.next()){
        return "Failed to cancel reservation " + reservationId + "\n";
      }
      else {
        int cost = getResult.getInt("cost");
        int fid1 = getResult.getInt("fid1");
        int fid2 = getResult.getInt("fid2");
        int iscanceled = getResult.getInt("canceled");
        getResult.close();

        if (iscanceled == 1){                             // if iscanceled, then return error
          return "Failed to cancel reservation " + reservationId + "\n";
        }

        findBalance.clearParameters();                    // get current balance
        findBalance.setString(1, this.loggedUser);
        ResultSet getBalance = findBalance.executeQuery();
        getBalance.next();
        int remainBalance = getBalance.getInt("balance");
        getBalance.close();

        updateBalance.clearParameters();                    // update balance
        updateBalance.setInt(1, cost + remainBalance);
        updateBalance.setString(2, this.loggedUser);
        updateBalance.execute();

        cancelReservation.clearParameters();                   // update reservation table
        cancelReservation.setInt(1, 0);
        cancelReservation.setInt(2, 1);
        cancelReservation.setInt(3, reservationId);
        cancelReservation.executeUpdate();

        // get 1st flight current capacity
        getCapacities.clearParameters();
        getCapacities.setInt(1, fid1);
        ResultSet capacity1 = getCapacities.executeQuery();
        capacity1.next();
        int remain1 = capacity1.getInt("capacity");
        capacity1.close();
        // update 1st flight capacity table
        addCapacity.clearParameters();
        addCapacity.setInt(1, remain1 + 1);
        addCapacity.setInt(2, fid1);
        addCapacity.execute();

        // get 2nd flight current capacity
        if (fid2 != 0) {
          getCapacities.clearParameters();
          getCapacities.setInt(1, fid2);
          ResultSet capacity2 = getCapacities.executeQuery();
          capacity2.next();
          int remain2 = capacity2.getInt("capacity");
          capacity2.close();
          // update 2nd flight capacity
          addCapacity.clearParameters();
          addCapacity.setInt(1, remain2 + 1);
          addCapacity.setInt(2, fid2);
          addCapacity.execute();
        }
        return "Canceled reservation " + reservationId + "\n";
      }


    } catch (SQLException e){
      e.printStackTrace();
    }
    return "Failed to cancel reservation " + reservationId + "\n";
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    checkFlightCapacityStatement.clearParameters();
    checkFlightCapacityStatement.setInt(1, fid);
    ResultSet results = checkFlightCapacityStatement.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }


  class Itinerary{
    public int fid1;
    public int fid2;
    public int day;
    public int cost;
    public int capacity;
  }
  /**
   * A class to store flight information.
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: " + flightNum + " Origin: "
              + originCity + " Dest: " + destCity + " Duration: " + time + " Capacity: " + capacity + " Price: " + price;
    }
  }
}
