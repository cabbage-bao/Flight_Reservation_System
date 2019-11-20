CREATE TABLE Users(username VARCHAR(20) PRIMARY KEY,
					password VARCHAR(20),
					balance INT);
CREATE TABLE Reservation(rid INT PRIMARY KEY,
						fid1 INT, 
						fid2 INT,
						paid INT, 
						username VARCHAR(20),
						cost INT,
						canceled INT);
CREATE TABLE Capacities(fid INT PRIMARY KEY, capacity INT);