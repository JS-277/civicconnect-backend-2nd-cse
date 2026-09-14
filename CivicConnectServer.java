package prototype.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CivicConnectServer {

    // =====================================================
    // UPLOAD DIRECTORY
    // =====================================================

    private static final Path UPLOAD_DIR =
            Paths.get("uploads").toAbsolutePath().normalize();


    // =====================================================
    // MAIN
    // =====================================================

    public static void main(String[] args) {

        try {

            Files.createDirectories(UPLOAD_DIR);

            int port = getPort();

            HttpServer server =
                    HttpServer.create(
                            new InetSocketAddress(port),
                            0
                    );

            server.createContext(
                    "/api/complaints",
                    CivicConnectServer::handleComplaints
            );

            server.createContext(
                    "/uploads",
                    CivicConnectServer::handleUploads
            );

            server.setExecutor(null);

            server.start();

            System.out.println("--------------------------------");
            System.out.println("CIVICCONNECT BACKEND SERVER");
            System.out.println("Server running at:");
            System.out.println("Server port: " + port);
            System.out.println("Upload directory: " + UPLOAD_DIR);
            System.out.println("--------------------------------");

            Connection connection =
                    DatabaseConnection.getConnection();

            if (connection != null) {

                connection.close();

                System.out.println(
                        "Railway/MySQL connected successfully!"
                );

                System.out.println(
                        "Database connection test: SUCCESS"
                );

            } else {

                System.out.println(
                        "Database connection failed!"
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "Server could not start!"
            );

            e.printStackTrace();
        }
    }


    // =====================================================
    // PORT
    // =====================================================

    private static int getPort() {

        String railwayPort =
                System.getenv("PORT");

        if (
                railwayPort != null
                        &&
                !railwayPort.isBlank()
        ) {

            return Integer.parseInt(
                    railwayPort
            );
        }

        return 8080;
    }


    // =====================================================
    // MAIN API HANDLER
    // =====================================================

    private static void handleComplaints(
            HttpExchange exchange
    ) throws IOException {

        addCorsHeaders(exchange);

        String method =
                exchange.getRequestMethod();

        String path =
                exchange.getRequestURI()
                        .getPath();


        // -------------------------------------------------
        // OPTIONS
        // -------------------------------------------------

        if (
                method.equalsIgnoreCase("OPTIONS")
        ) {

            exchange.sendResponseHeaders(
                    200,
                    -1
            );

            exchange.close();

            return;
        }


        // -------------------------------------------------
        // POST
        // -------------------------------------------------

        if (
                method.equalsIgnoreCase("POST")
                        &&
                path.equals(
                        "/api/complaints"
                )
        ) {

            handlePostComplaint(exchange);

            return;
        }


        // -------------------------------------------------
        // GET
        // -------------------------------------------------

        if (
                method.equalsIgnoreCase("GET")
        ) {

            handleGetComplaints(exchange);

            return;
        }


        // -------------------------------------------------
        // PUT STATUS
        // -------------------------------------------------

        if (
                method.equalsIgnoreCase("PUT")
                        &&
                path.startsWith(
                        "/api/complaints/"
                )
                        &&
                path.endsWith("/status")
        ) {

            handleUpdateStatus(exchange);

            return;
        }


        sendResponse(
                exchange,
                405,
                createErrorJson(
                        "Method not allowed"
                )
        );
    }


    // =====================================================
    // CORS
    // =====================================================

    private static void addCorsHeaders(
            HttpExchange exchange
    ) {

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*"
        );

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, PUT, OPTIONS"
        );

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type"
        );
    }


    // =====================================================
    // POST COMPLAINT
    // =====================================================

    private static void handlePostComplaint(
            HttpExchange exchange
    ) throws IOException {

        Path savedFile = null;

        try {

            String contentType =
                    exchange.getRequestHeaders()
                            .getFirst(
                                    "Content-Type"
                            );


            if (
                    contentType == null
                            ||
                    !contentType
                            .toLowerCase()
                            .startsWith(
                                    "multipart/form-data"
                            )
            ) {

                sendResponse(
                        exchange,
                        400,
                        createErrorJson(
                                "Complaint submission must use multipart/form-data"
                        )
                );

                return;
            }


            Map<String, String> data =
                    new HashMap<>();


            MultipartFile uploadedFile =
                    parseMultipart(
                            exchange.getRequestBody(),
                            contentType,
                            data
                    );


            // =================================================
            // FORM FIELDS
            // =================================================

            String name =
                    data.getOrDefault(
                            "name",
                            ""
                    ).trim();

            String phone =
                    data.getOrDefault(
                            "phone",
                            ""
                    ).trim();

            String district =
                    data.getOrDefault(
                            "district",
                            ""
                    ).trim();

            String category =
                    data.getOrDefault(
                            "category",
                            ""
                    ).trim();

            String description =
                    data.getOrDefault(
                            "description",
                            ""
                    ).trim();

            String location =
                    data.getOrDefault(
                            "location",
                            ""
                    ).trim();

            String areaType =
                    data.getOrDefault(
                            "areaType",
                            ""
                    ).trim();

            String blockName =
                    data.getOrDefault(
                            "blockName",
                            ""
                    ).trim();

            String panchayat =
                    data.getOrDefault(
                            "panchayat",
                            ""
                    ).trim();

            String municipality =
                    data.getOrDefault(
                            "municipality",
                            ""
                    ).trim();

            String wardNumber =
                    data.getOrDefault(
                            "wardNumber",
                            ""
                    ).trim();

            String grievanceType =
                    data.getOrDefault(
                            "grievanceType",
                            ""
                    ).trim();

            String petitionSubject =
                    data.getOrDefault(
                            "petitionSubject",
                            ""
                    ).trim();


            // =================================================
            // VALIDATION
            // =================================================

            if (name.isEmpty()) {

                sendResponse(
                        exchange,
                        400,
                        createErrorJson(
                                "Name is required"
                        )
                );

                return;
            }


            if (
                    !phone.matches(
                            "[0-9]{10}"
                    )
            ) {

                sendResponse(
                        exchange,
                        400,
                        createErrorJson(
                                "Enter a valid 10-digit phone number"
                        )
                );

                return;
            }


            if (district.isEmpty()) {

                sendResponse(
                        exchange,
                        400,
                        createErrorJson(
                                "District is required"
                        )
                );

                return;
            }


            if (category.isEmpty()) {

                sendResponse(
                        exchange,
                        400,
                        createErrorJson(
                                "Category is required"
                        )
                );

                return;
            }


            if (
                    description.length() < 10
            ) {

                sendResponse(
                        exchange,
                        400,
                        createErrorJson(
                                "Description must contain at least 10 characters"
                        )
                );

                return;
            }


            if (location.isEmpty()) {

                sendResponse(
                        exchange,
                        400,
                        createErrorJson(
                                "Location is required"
                        )
                );

                return;
            }


            // =================================================
            // EVIDENCE
            // =================================================

            String evidenceType = "";


            if (uploadedFile != null) {

                String originalName =
                        uploadedFile.fileName == null
                                ? ""
                                : uploadedFile.fileName;

                String lowerName =
                        originalName.toLowerCase();

                String uploadedContentType =
                        uploadedFile.contentType == null
                                ? ""
                                : uploadedFile.contentType
                                        .toLowerCase();


                // -------------------------------------------------
                // PDF
                // -------------------------------------------------

                if (
                        lowerName.endsWith(".pdf")
                                ||
                        uploadedContentType.contains(
                                "application/pdf"
                        )
                ) {

                    evidenceType = "PDF";


                    if (
                            !isGrievancePetition(
                                    grievanceType
                            )
                    ) {

                        sendResponse(
                                exchange,
                                400,
                                createErrorJson(
                                        "PDF evidence is allowed only for Grievance Petition"
                                )
                        );

                        return;
                    }


                // -------------------------------------------------
                // IMAGE
                // -------------------------------------------------

                } else if (
                        lowerName.endsWith(".jpg")
                                ||
                        lowerName.endsWith(".jpeg")
                                ||
                        lowerName.endsWith(".png")
                                ||
                        lowerName.endsWith(".webp")
                                ||
                        uploadedContentType.startsWith(
                                "image/"
                        )
                ) {

                    evidenceType = "IMAGE";


                    if (
                            !isCivicGrievance(
                                    grievanceType
                            )
                    ) {

                        sendResponse(
                                exchange,
                                400,
                                createErrorJson(
                                        "Image evidence is allowed only for Civic Grievance"
                                )
                        );

                        return;
                    }


                } else {

                    sendResponse(
                            exchange,
                            400,
                            createErrorJson(
                                    "Only image or PDF evidence is allowed"
                            )
                    );

                    return;
                }
            }


            // -------------------------------------------------
            // CIVIC GRIEVANCE
            // -------------------------------------------------

            if (
                    isCivicGrievance(
                            grievanceType
                    )
                            &&
                    uploadedFile == null
            ) {

                sendResponse(
                        exchange,
                        400,
                        createErrorJson(
                                "Photo evidence is required for Civic Grievance"
                        )
                );

                return;
            }


            // -------------------------------------------------
            // GRIEVANCE PETITION
            // -------------------------------------------------

            if (
                    isGrievancePetition(
                            grievanceType
                    )
                            &&
                    uploadedFile == null
            ) {

                sendResponse(
                        exchange,
                        400,
                        createErrorJson(
                                "PDF petition evidence is required for Grievance Petition"
                        )
                );

                return;
            }


            // =================================================
            // SAVE FILE LOCALLY
            // =================================================

            if (uploadedFile != null) {

                String fileName =
                        createSafeEvidenceFileName(
                                uploadedFile.fileName,
                                evidenceType
                        );


                savedFile =
                        UPLOAD_DIR
                                .resolve(fileName)
                                .normalize();


                if (
                        !savedFile.startsWith(
                                UPLOAD_DIR
                        )
                ) {

                    throw new IOException(
                            "Invalid upload file path"
                    );
                }


                Files.write(
                        savedFile,
                        uploadedFile.bytes
                );
            }


            String evidenceFile =
                    savedFile == null
                            ? ""
                            : savedFile
                                    .getFileName()
                                    .toString();


            // =================================================
            // SAVE COMPLAINT
            // =================================================

            int id =
                    saveComplaint(
                            name,
                            phone,
                            district,
                            category,
                            description,
                            location,
                            areaType,
                            blockName,
                            panchayat,
                            municipality,
                            wardNumber,
                            grievanceType,
                            petitionSubject,
                            evidenceType,
                            evidenceFile
                    );


            String complaintId =
                    "CC" +
                    String.format(
                            "%03d",
                            id
                    );


            String response =
                    "{"
                    + "\"success\":true,"
                    + "\"id\":" + id + ","
                    + "\"complaintId\":\""
                    + escapeJson(
                            complaintId
                    )
                    + "\","
                    + "\"evidenceType\":\""
                    + escapeJson(
                            evidenceType
                    )
                    + "\","
                    + "\"evidenceFile\":\""
                    + escapeJson(
                            evidenceFile
                    )
                    + "\","
                    + "\"message\":\"Complaint submitted successfully!\""
                    + "}";


            sendResponse(
                    exchange,
                    200,
                    response
            );


        } catch (Exception e) {

            if (savedFile != null) {

                try {

                    Files.deleteIfExists(
                            savedFile
                    );

                } catch (IOException ignored) {
                }
            }


            e.printStackTrace();


            sendResponse(
                    exchange,
                    500,
                    createErrorJson(
                            "Failed to submit complaint"
                    )
            );
        }
    }


    // =====================================================
    // GRIEVANCE TYPE
    // =====================================================

    private static boolean isCivicGrievance(
            String grievanceType
    ) {

        return grievanceType.equalsIgnoreCase(
                "Civic Grievance"
        );
    }


    private static boolean isGrievancePetition(
            String grievanceType
    ) {

        return grievanceType.equalsIgnoreCase(
                "Grievance Petition"
        );
    }


    // =====================================================
    // CREATE SAFE FILE NAME
    // =====================================================

    private static String createSafeEvidenceFileName(
            String originalName,
            String evidenceType
    ) {

        String extension = "";


        if (originalName != null) {

            String lower =
                    originalName.toLowerCase();


            if (
                    evidenceType.equals("PDF")
                            &&
                    lower.endsWith(".pdf")
            ) {

                extension = ".pdf";

            } else if (
                    lower.endsWith(".jpeg")
            ) {

                extension = ".jpeg";

            } else if (
                    lower.endsWith(".jpg")
            ) {

                extension = ".jpg";

            } else if (
                    lower.endsWith(".png")
            ) {

                extension = ".png";

            } else if (
                    lower.endsWith(".webp")
            ) {

                extension = ".webp";
            }
        }


        return "evidence_" +
                UUID.randomUUID() +
                extension;
    }


    // =====================================================
    // GET COMPLAINTS
    // =====================================================

    private static void handleGetComplaints(
            HttpExchange exchange
    ) throws IOException {

        try {

            String query =
                    exchange.getRequestURI()
                            .getQuery();


            // -------------------------------------------------
            // SINGLE COMPLAINT
            // -------------------------------------------------

            if (
                    query != null
                            &&
                    query.startsWith("id=")
            ) {

                String idText =
                        query.substring(3);

                int id;


                try {

                    id =
                            Integer.parseInt(
                                    idText
                            );

                } catch (
                        NumberFormatException e
                ) {

                    sendResponse(
                            exchange,
                            400,
                            createErrorJson(
                                    "Invalid complaint ID"
                            )
                    );

                    return;
                }


                String complaint =
                        getComplaintById(id);


                if (complaint == null) {

                    sendResponse(
                            exchange,
                            404,
                            createErrorJson(
                                    "Complaint not found"
                            )
                    );

                } else {

                    sendResponse(
                            exchange,
                            200,
                            complaint
                    );
                }

                return;
            }


            // -------------------------------------------------
            // ALL COMPLAINTS
            // -------------------------------------------------

            String complaints =
                    getComplaints();


            sendResponse(
                    exchange,
                    200,
                    complaints
            );


        } catch (Exception e) {

            e.printStackTrace();


            sendResponse(
                    exchange,
                    500,
                    createErrorJson(
                            "Failed to get complaints"
                    )
            );
        }
    }


    // =====================================================
    // UPDATE STATUS
    // =====================================================

    private static void handleUpdateStatus(
            HttpExchange exchange
    ) throws IOException {

        try {

            String path =
                    exchange.getRequestURI()
                            .getPath();


            String prefix =
                    "/api/complaints/";

            String suffix =
                    "/status";


            String idText =
                    path.substring(
                            prefix.length(),
                            path.length()
                                    - suffix.length()
                    );


            int id;


            try {

                id =
                        Integer.parseInt(
                                idText
                        );

            } catch (
                    NumberFormatException e
            ) {

                sendResponse(
                        exchange,
                        400,
                        createErrorJson(
                                "Invalid complaint ID"
                        )
                );

                return;
            }


            String body =
                    readRequestBody(
                            exchange.getRequestBody()
                    );


            Map<String, String> data =
                    parseFormData(
                            body
                    );


            String status =
                    data.getOrDefault(
                            "status",
                            ""
                    ).trim();


            if (
                    !status.equals("Pending")
                            &&
                    !status.equals("In Progress")
                            &&
                    !status.equals("Resolved")
            ) {

                sendResponse(
                        exchange,
                        400,
                        createErrorJson(
                                "Invalid status"
                        )
                );

                return;
            }


            boolean updated =
                    updateComplaintStatus(
                            id,
                            status
                    );


            if (!updated) {

                sendResponse(
                        exchange,
                        404,
                        createErrorJson(
                                "Complaint not found"
                        )
                );

                return;
            }


            System.out.println("--------------------------------");
            System.out.println(
                    "Complaint status updated"
            );
            System.out.println(
                    "Database ID: " + id
            );
            System.out.println(
                    "New Status: " + status
            );
            System.out.println("--------------------------------");


            String response =
                    "{"
                    + "\"success\":true,"
                    + "\"id\":" + id + ","
                    + "\"status\":\""
                    + escapeJson(status)
                    + "\","
                    + "\"message\":\"Complaint status updated successfully!\""
                    + "}";


            sendResponse(
                    exchange,
                    200,
                    response
            );


        } catch (Exception e) {

            e.printStackTrace();


            sendResponse(
                    exchange,
                    500,
                    createErrorJson(
                            "Failed to update complaint status"
                    )
            );
        }
    }


    // =====================================================
    // SAVE COMPLAINT
    // =====================================================

    private static int saveComplaint(
            String name,
            String phone,
            String district,
            String category,
            String description,
            String location,
            String areaType,
            String blockName,
            String panchayat,
            String municipality,
            String wardNumber,
            String grievanceType,
            String petitionSubject,
            String evidenceType,
            String evidenceFile
    ) throws SQLException {


        String insertSql =
                "INSERT INTO complaints " +
                "(name, phone, district, category, description, location, " +
                "area_type, block_name, panchayat, municipality, ward_number, " +
                "grievance_type, petition_subject, evidence_type, evidence_file) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";


        String updateSql =
                "UPDATE complaints " +
                "SET complaint_id = ? " +
                "WHERE id = ?";


        Connection connection =
                DatabaseConnection.getConnection();


        if (connection == null) {

            throw new SQLException(
                    "Database connection is null."
            );
        }


        try {

            connection.setAutoCommit(false);


            try (
                    PreparedStatement statement =
                            connection.prepareStatement(
                                    insertSql,
                                    Statement.RETURN_GENERATED_KEYS
                            )
            ) {

                statement.setString(
                        1,
                        name
                );

                statement.setString(
                        2,
                        phone
                );

                statement.setString(
                        3,
                        district
                );

                statement.setString(
                        4,
                        category
                );

                statement.setString(
                        5,
                        description
                );

                statement.setString(
                        6,
                        location
                );

                statement.setString(
                        7,
                        areaType
                );

                statement.setString(
                        8,
                        blockName
                );

                statement.setString(
                        9,
                        panchayat
                );

                statement.setString(
                        10,
                        municipality
                );

                statement.setString(
                        11,
                        wardNumber
                );

                statement.setString(
                        12,
                        grievanceType
                );

                statement.setString(
                        13,
                        petitionSubject
                );

                statement.setString(
                        14,
                        evidenceType
                );

                statement.setString(
                        15,
                        evidenceFile
                );


                statement.executeUpdate();


                try (
                        ResultSet keys =
                                statement.getGeneratedKeys()
                ) {

                    if (!keys.next()) {

                        throw new SQLException(
                                "Could not get generated ID."
                        );
                    }


                    int id =
                            keys.getInt(1);


                    String complaintId =
                            "CC" +
                            String.format(
                                    "%03d",
                                    id
                            );


                    try (
                            PreparedStatement update =
                                    connection.prepareStatement(
                                            updateSql
                                    )
                    ) {

                        update.setString(
                                1,
                                complaintId
                        );

                        update.setInt(
                                2,
                                id
                        );

                        update.executeUpdate();
                    }


                    connection.commit();


                    System.out.println("--------------------------------");
                    System.out.println("New complaint saved!");
                    System.out.println("Database ID: " + id);
                    System.out.println("Complaint ID: " + complaintId);
                    System.out.println("Evidence Type: " + evidenceType);
                    System.out.println("Evidence File: " + evidenceFile);
                    System.out.println("--------------------------------");


                    return id;
                }
            }


        } catch (SQLException e) {

            try {

                connection.rollback();

            } catch (SQLException rollbackError) {

                rollbackError.printStackTrace();
            }

            throw e;


        } finally {

            try {

                connection.setAutoCommit(true);

            } catch (SQLException e) {

                e.printStackTrace();
            }


            try {

                connection.close();

            } catch (SQLException e) {

                e.printStackTrace();
            }
        }
    }


    // =====================================================
    // UPDATE DATABASE STATUS
    // =====================================================

    private static boolean updateComplaintStatus(
            int id,
            String status
    ) throws SQLException {


        String sql =
                "UPDATE complaints " +
                "SET status = ? " +
                "WHERE id = ?";


        Connection connection =
                DatabaseConnection.getConnection();


        if (connection == null) {

            throw new SQLException(
                    "Database connection is null."
            );
        }


        try (
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(
                    1,
                    status
            );

            statement.setInt(
                    2,
                    id
            );


            int rows =
                    statement.executeUpdate();


            return rows > 0;


        } finally {

            connection.close();
        }
    }


    // =====================================================
    // GET SINGLE COMPLAINT
    // =====================================================

    private static String getComplaintById(
            int id
    ) throws SQLException {


        String sql =
                "SELECT id, complaint_id, name, phone, " +
                "district, category, description, location, " +
                "status, created_at, area_type, block_name, " +
                "panchayat, municipality, ward_number, " +
                "grievance_type, petition_subject, " +
                "evidence_type, evidence_file " +
                "FROM complaints " +
                "WHERE id = ?";


        Connection connection =
                DatabaseConnection.getConnection();


        if (connection == null) {

            throw new SQLException(
                    "Database connection is null."
            );
        }


        try (
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(
                    1,
                    id
            );


            try (
                    ResultSet result =
                            statement.executeQuery()
            ) {

                if (!result.next()) {

                    return null;
                }


                String complaintId =
                        result.getString(
                                "complaint_id"
                        );


                if (
                        complaintId == null
                                ||
                        complaintId.isEmpty()
                ) {

                    complaintId =
                            "CC" +
                            String.format(
                                    "%03d",
                                    id
                            );
                }


                return createComplaintJson(
                        result,
                        id,
                        complaintId
                );
            }


        } finally {

            connection.close();
        }
    }


    // =====================================================
    // GET ALL COMPLAINTS
    // =====================================================

    private static String getComplaints()
            throws SQLException {


        String sql =
                "SELECT id, complaint_id, name, phone, " +
                "district, category, description, location, " +
                "status, created_at, area_type, block_name, " +
                "panchayat, municipality, ward_number, " +
                "grievance_type, petition_subject, " +
                "evidence_type, evidence_file " +
                "FROM complaints " +
                "ORDER BY id DESC";


        Connection connection =
                DatabaseConnection.getConnection();


        if (connection == null) {

            throw new SQLException(
                    "Database connection is null."
            );
        }


        StringBuilder json =
                new StringBuilder();


        json.append("[");


        try (
                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet result =
                        statement.executeQuery()
        ) {

            boolean first = true;


            while (result.next()) {

                if (!first) {

                    json.append(",");
                }

                first = false;


                int id =
                        result.getInt(
                                "id"
                        );


                String complaintId =
                        result.getString(
                                "complaint_id"
                        );


                if (
                        complaintId == null
                                ||
                        complaintId.isEmpty()
                ) {

                    complaintId =
                            "CC" +
                            String.format(
                                    "%03d",
                                    id
                            );
                }


                json.append(
                        createComplaintJson(
                                result,
                                id,
                                complaintId
                        )
                );
            }
        }


        json.append("]");


        connection.close();


        return json.toString();
    }


    // =====================================================
    // CREATE COMPLAINT JSON
    // =====================================================

    private static String createComplaintJson(
            ResultSet result,
            int id,
            String complaintId
    ) throws SQLException {


        String name =
                escapeJson(
                        result.getString(
                                "name"
                        )
                );

        String phone =
                escapeJson(
                        result.getString(
                                "phone"
                        )
                );

        String district =
                escapeJson(
                        result.getString(
                                "district"
                        )
                );

        String category =
                escapeJson(
                        result.getString(
                                "category"
                        )
                );

        String description =
                escapeJson(
                        result.getString(
                                "description"
                        )
                );

        String location =
                escapeJson(
                        result.getString(
                                "location"
                        )
                );

        String status =
                escapeJson(
                        result.getString(
                                "status"
                        )
                );

        String createdAt =
                escapeJson(
                        result.getString(
                                "created_at"
                        )
                );

        String areaType =
                escapeJson(
                        result.getString(
                                "area_type"
                        )
                );

        String blockName =
                escapeJson(
                        result.getString(
                                "block_name"
                        )
                );

        String panchayat =
                escapeJson(
                        result.getString(
                                "panchayat"
                        )
                );

        String municipality =
                escapeJson(
                        result.getString(
                                "municipality"
                        )
                );

        String wardNumber =
                escapeJson(
                        result.getString(
                                "ward_number"
                        )
                );

        String grievanceType =
                escapeJson(
                        result.getString(
                                "grievance_type"
                        )
                );

        String petitionSubject =
                escapeJson(
                        result.getString(
                                "petition_subject"
                        )
                );

        String evidenceType =
                escapeJson(
                        result.getString(
                                "evidence_type"
                        )
                );

        String evidenceFile =
                escapeJson(
                        result.getString(
                                "evidence_file"
                        )
                );


        return "{"
                + "\"id\":" + id + ","
                + "\"complaintId\":\""
                + escapeJson(complaintId)
                + "\","
                + "\"name\":\""
                + name
                + "\","
                + "\"phone\":\""
                + phone
                + "\","
                + "\"district\":\""
                + district
                + "\","
                + "\"category\":\""
                + category
                + "\","
                + "\"description\":\""
                + description
                + "\","
                + "\"location\":\""
                + location
                + "\","
                + "\"status\":\""
                + status
                + "\","
                + "\"createdAt\":\""
                + createdAt
                + "\","
                + "\"areaType\":\""
                + areaType
                + "\","
                + "\"blockName\":\""
                + blockName
                + "\","
                + "\"panchayat\":\""
                + panchayat
                + "\","
                + "\"municipality\":\""
                + municipality
                + "\","
                + "\"wardNumber\":\""
                + wardNumber
                + "\","
                + "\"grievanceType\":\""
                + grievanceType
                + "\","
                + "\"petitionSubject\":\""
                + petitionSubject
                + "\","
                + "\"evidenceType\":\""
                + evidenceType
                + "\","
                + "\"evidenceFile\":\""
                + evidenceFile
                + "\""
                + "}";
    }


    // =====================================================
    // MULTIPART PARSER
    // =====================================================

    private static MultipartFile parseMultipart(
            InputStream inputStream,
            String contentType,
            Map<String, String> fields
    ) throws IOException {


        String boundary =
                extractBoundary(
                        contentType
                );


        if (
                boundary == null
                        ||
                boundary.isEmpty()
        ) {

            throw new IOException(
                    "Multipart boundary not found"
            );
        }


        byte[] body =
                readAllBytes(
                        inputStream
                );


        String bodyText =
                new String(
                        body,
                        StandardCharsets.ISO_8859_1
                );


        String delimiter =
                "--" + boundary;


        String[] parts =
                bodyText.split(
                        java.util.regex.Pattern.quote(
                                delimiter
                        )
                );


        MultipartFile uploadedFile =
                null;


        for (String part : parts) {

            if (
                    part == null
                            ||
                    part.isBlank()
                            ||
                    part.equals("--")
            ) {

                continue;
            }


            if (part.startsWith("--")) {

                continue;
            }


            int headerEnd =
                    part.indexOf(
                            "\r\n\r\n"
                    );


            if (headerEnd < 0) {

                continue;
            }


            String headers =
                    part.substring(
                            0,
                            headerEnd
                    );


            String content =
                    part.substring(
                            headerEnd + 4
                    );


            if (
                    content.endsWith(
                            "\r\n"
                    )
            ) {

                content =
                        content.substring(
                                0,
                                content.length() - 2
                        );
            }


            String disposition =
                    getHeaderValue(
                            headers,
                            "Content-Disposition"
                    );


            if (disposition == null) {

                continue;
            }


            String fieldName =
                    getDispositionParameter(
                            disposition,
                            "name"
                    );


            String fileName =
                    getDispositionParameter(
                            disposition,
                            "filename"
                    );


            // -------------------------------------------------
            // FILE
            // -------------------------------------------------

            if (
                    fileName != null
                            &&
                    !fileName.isEmpty()
            ) {

                String partContentType =
                        getHeaderValue(
                                headers,
                                "Content-Type"
                        );


                byte[] fileBytes =
                        content.getBytes(
                                StandardCharsets.ISO_8859_1
                        );


                uploadedFile =
                        new MultipartFile(
                                sanitizeOriginalFileName(
                                        fileName
                                ),
                                partContentType,
                                fileBytes
                        );


            // -------------------------------------------------
            // NORMAL FIELD
            // -------------------------------------------------

            } else if (
                    fieldName != null
            ) {

                fields.put(
                        fieldName,
                        content
                );
            }
        }


        return uploadedFile;
    }


    // =====================================================
    // EXTRACT BOUNDARY
    // =====================================================

    private static String extractBoundary(
            String contentType
    ) {

        String[] pieces =
                contentType.split(";");


        for (String piece : pieces) {

            String trimmed =
                    piece.trim();


            if (
                    trimmed.toLowerCase()
                            .startsWith(
                                    "boundary="
                            )
            ) {

                String boundary =
                        trimmed.substring(
                                "boundary=".length()
                        ).trim();


                if (
                        boundary.startsWith("\"")
                                &&
                        boundary.endsWith("\"")
                ) {

                    boundary =
                            boundary.substring(
                                    1,
                                    boundary.length() - 1
                            );
                }


                return boundary;
            }
        }


        return null;
    }


    // =====================================================
    // GET HEADER VALUE
    // =====================================================

    private static String getHeaderValue(
            String headers,
            String headerName
    ) {

        String[] lines =
                headers.split(
                        "\r\n"
                );


        for (String line : lines) {

            int colon =
                    line.indexOf(":");


            if (colon < 0) {

                continue;
            }


            String name =
                    line.substring(
                            0,
                            colon
                    ).trim();


            if (
                    name.equalsIgnoreCase(
                            headerName
                    )
            ) {

                return line.substring(
                        colon + 1
                ).trim();
            }
        }


        return null;
    }


    // =====================================================
    // GET DISPOSITION PARAMETER
    // =====================================================

    private static String getDispositionParameter(
            String disposition,
            String parameter
    ) {

        String[] pieces =
                disposition.split(";");


        for (String piece : pieces) {

            String trimmed =
                    piece.trim();


            String prefix =
                    parameter + "=";


            if (
                    trimmed.toLowerCase()
                            .startsWith(
                                    prefix.toLowerCase()
                            )
            ) {

                String value =
                        trimmed.substring(
                                prefix.length()
                        ).trim();


                if (
                        value.startsWith("\"")
                                &&
                        value.endsWith("\"")
                ) {

                    value =
                            value.substring(
                                    1,
                                    value.length() - 1
                            );
                }


                return value;
            }
        }


        return null;
    }


    // =====================================================
    // SANITIZE FILE NAME
    // =====================================================

    private static String sanitizeOriginalFileName(
            String fileName
    ) {

        if (fileName == null) {

            return "";
        }


        String normalized =
                fileName.replace(
                        "\\",
                        "/"
                );


        int slash =
                normalized.lastIndexOf("/");


        if (slash >= 0) {

            normalized =
                    normalized.substring(
                            slash + 1
                    );
        }


        return normalized.replaceAll(
                "[^a-zA-Z0-9._-]",
                "_"
        );
    }


    // =====================================================
    // READ ALL BYTES
    // =====================================================

    private static byte[] readAllBytes(
            InputStream inputStream
    ) throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();


        byte[] buffer =
                new byte[8192];


        int length;


        while (
                (length =
                        inputStream.read(buffer))
                        != -1
        ) {

            output.write(
                    buffer,
                    0,
                    length
            );
        }


        return output.toByteArray();
    }


    // =====================================================
    // MULTIPART FILE
    // =====================================================

    private static class MultipartFile {

        private final String fileName;
        private final String contentType;
        private final byte[] bytes;


        private MultipartFile(
                String fileName,
                String contentType,
                byte[] bytes
        ) {

            this.fileName =
                    fileName;

            this.contentType =
                    contentType;

            this.bytes =
                    bytes;
        }
    }


    // =====================================================
    // SERVE UPLOADED FILES
    // =====================================================

    private static void handleUploads(
            HttpExchange exchange
    ) throws IOException {


        if (
                !exchange.getRequestMethod()
                        .equalsIgnoreCase(
                                "GET"
                        )
        ) {

            sendResponse(
                    exchange,
                    405,
                    "Method not allowed"
            );

            return;
        }


        String requestPath =
                exchange.getRequestURI()
                        .getPath();


        String relative =
                requestPath.substring(
                        "/uploads".length()
                );


        if (
                relative.startsWith("/")
        ) {

            relative =
                    relative.substring(1);
        }


        if (relative.isEmpty()) {

            sendResponse(
                    exchange,
                    404,
                    "File not found"
            );

            return;
        }


        Path requestedFile =
                UPLOAD_DIR
                        .resolve(relative)
                        .normalize();


        if (
                !requestedFile.startsWith(
                        UPLOAD_DIR
                )
        ) {

            sendResponse(
                    exchange,
                    403,
                    "Forbidden"
            );

            return;
        }


        if (
                !Files.exists(
                        requestedFile
                )
                        ||
                !Files.isRegularFile(
                        requestedFile
                )
        ) {

            sendResponse(
                    exchange,
                    404,
                    "File not found"
            );

            return;
        }


        String fileContentType =
                Files.probeContentType(
                        requestedFile
                );


        if (fileContentType == null) {

            if (
                    requestedFile.toString()
                            .toLowerCase()
                            .endsWith(".pdf")
            ) {

                fileContentType =
                        "application/pdf";

            } else {

                fileContentType =
                        "application/octet-stream";
            }
        }


        exchange.getResponseHeaders().set(
                "Content-Type",
                fileContentType
        );


        long size =
                Files.size(
                        requestedFile
                );


        exchange.sendResponseHeaders(
                200,
                size
        );


        try (
                OutputStream output =
                        exchange.getResponseBody()
        ) {

            Files.copy(
                    requestedFile,
                    output
            );
        }
    }


    // =====================================================
    // FORM DATA PARSER
    // =====================================================

    private static Map<String, String> parseFormData(
            String formData
    ) throws Exception {

        Map<String, String> data =
                new HashMap<>();


        if (
                formData == null
                        ||
                formData.isEmpty()
        ) {

            return data;
        }


        String[] pairs =
                formData.split("&");


        for (String pair : pairs) {

            String[] parts =
                    pair.split(
                            "=",
                            2
                    );


            if (parts.length == 2) {

                String key =
                        URLDecoder.decode(
                                parts[0],
                                StandardCharsets.UTF_8.name()
                        );


                String value =
                        URLDecoder.decode(
                                parts[1],
                                StandardCharsets.UTF_8.name()
                        );


                data.put(
                        key,
                        value
                );
            }
        }


        return data;
    }


    // =====================================================
    // READ REQUEST BODY
    // =====================================================

    private static String readRequestBody(
            InputStream inputStream
    ) throws IOException {

        return new String(
                readAllBytes(
                        inputStream
                ),
                StandardCharsets.UTF_8
        );
    }


    // =====================================================
    // SEND RESPONSE
    // =====================================================

    private static void sendResponse(
            HttpExchange exchange,
            int statusCode,
            String response
    ) throws IOException {

        String contentType;


        if (
                response.startsWith("{")
                        ||
                response.startsWith("[")
        ) {

            contentType =
                    "application/json; charset=UTF-8";

        } else {

            contentType =
                    "text/plain; charset=UTF-8";
        }


        exchange.getResponseHeaders().set(
                "Content-Type",
                contentType
        );


        byte[] responseBytes =
                response.getBytes(
                        StandardCharsets.UTF_8
                );


        exchange.sendResponseHeaders(
                statusCode,
                responseBytes.length
        );


        try (
                OutputStream output =
                        exchange.getResponseBody()
        ) {

            output.write(
                    responseBytes
            );
        }
    }


    // =====================================================
    // ERROR JSON
    // =====================================================

    private static String createErrorJson(
            String message
    ) {

        return "{"
                + "\"success\":false,"
                + "\"message\":\""
                + escapeJson(message)
                + "\""
                + "}";
    }


    // =====================================================
    // JSON ESCAPE
    // =====================================================

    private static String escapeJson(
            String text
    ) {

        if (text == null) {

            return "";
        }


        return text
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\t",
                        "\\t"
                );
    }
}