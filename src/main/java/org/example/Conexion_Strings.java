package org.example;

import oracle.jdbc.OracleConnection;

import java.sql.*;

public class Conexion_Strings {
    public static void main(String[] args) {
        // Datos de conexión de FreeSQL (no dejes la contraseña real en el código)
        String url = "jdbc:oracle:thin:@(description=(address=(protocol=tcps)(port=2484)(host=db.freesql.com))"
                + "(connect_data=(service_name=26ai_un3c1)))";
        String username = "Your_user_name";
        String password = "Your_password";

        // 1-2. Conectar (la conexión se cierra sola al terminar)
        try (Connection conexion = DriverManager.getConnection(url, username, password)) {
            System.out.println("Conectado a Oracle como " + conexion.getMetaData().getUserName());

            // 3. Mostrar datos de un cliente concreto
            // nombre es VARCHAR2, direccion es un objeto (llega como Struct)
            // y telefonos es un VARRAY (llega como Array) -> SQL:1999
            int id = 102;
            String sql = "SELECT nombre, direccion, telefonos FROM cliente WHERE id = ?";

            try (PreparedStatement ps = conexion.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Cliente: " + rs.getString("nombre"));

                        Struct direccion = (Struct) rs.getObject("direccion");
                        if (direccion != null) {
                            Object[] atributos_direccion = direccion.getAttributes();
                            System.out.println("Calle: "  + atributos_direccion[0]);
                            System.out.println("Ciudad: " + atributos_direccion[1]);
                            System.out.println("CP: "     + atributos_direccion[2]);
                        }

                        Array telefonos = rs.getArray("telefonos");
                        if (telefonos != null) {
                            try {
                                Object[] numeros_telefonos = (Object[]) telefonos.getArray();
                                for (int i = 0; i < numeros_telefonos.length; i++) {
                                    System.out.println("Telefono " + (i + 1) + ": " + numeros_telefonos[i]);
                                }
                            } finally {
                                telefonos.free();
                            }
                        }
                    } else {
                        System.out.println("No existe el cliente: " + id);
                    }
                }
            } // FIN CONSULTA

            // 4. Insertar un cliente nuevo
            int id_nuevo = 103;
            String nombre_nuevo = "Autos Complutense";
            String calle_nueva = "Calle Libreros 26";
            String ciudad_nueva = "Alcala de Henares";
            String cp_nuevo = "28500";
            String[] telefonos_nuevos = {"918000010", "600111222"};

            Struct nueva_direccion = conexion.createStruct("DIRECCION_T",
                    new Object[]{calle_nueva, ciudad_nueva, cp_nuevo});
            Array nuevos_telefonos = conexion.unwrap(OracleConnection.class)
                    .createOracleArray("TELEFONOS_T", telefonos_nuevos);

            String sql_insert = "INSERT INTO cliente (id, nombre, direccion, telefonos) VALUES (?,?,?,?)";
            try (PreparedStatement ps = conexion.prepareStatement(sql_insert)) {
                ps.setInt(1, id_nuevo);
                ps.setString(2, nombre_nuevo);
                ps.setObject(3, nueva_direccion);
                ps.setArray(4, nuevos_telefonos);
                ps.executeUpdate();
                System.out.println("Cliente " + id_nuevo + " creado");
            } catch (SQLIntegrityConstraintViolationException e) {
                System.out.println("El cliente " + id_nuevo + " ya existe");
            } finally {
                nuevos_telefonos.free();
            } // FIN INSERT

            // 5. Listar todos los clientes
            String sql_todos = "SELECT id, nombre, direccion, telefonos FROM cliente ORDER BY id";
            try (PreparedStatement ps = conexion.prepareStatement(sql_todos);
                 ResultSet rs = ps.executeQuery()) {

                System.out.println("\n===== LISTADO DE CLIENTES =====");
                int total = 0;

                while (rs.next()) {
                    total++;
                    System.out.println("\nID: " + rs.getInt("id"));
                    System.out.println("Cliente: " + rs.getString("nombre"));

                    Struct dir = (Struct) rs.getObject("direccion");
                    if (dir != null) {
                        Object[] attr = dir.getAttributes();
                        System.out.println("Calle: "  + attr[0]);
                        System.out.println("Ciudad: " + attr[1]);
                        System.out.println("CP: "     + attr[2]);
                    } else {
                        System.out.println("Direccion: (sin datos)");
                    }

                    Array tels = rs.getArray("telefonos");
                    if (tels != null) {
                        try {
                            Object[] nums = (Object[]) tels.getArray();
                            for (int i = 0; i < nums.length; i++) {
                                System.out.println("Telefono " + (i + 1) + ": " + nums[i]);
                            }
                        } finally {
                            tels.free();
                        }
                    } else {
                        System.out.println("Telefonos: (sin datos)");
                    }
                }
                System.out.println("\nTotal de clientes: " + total);
            } // FIN LISTADO

            // 6. Llamar a un procedimiento almacenado
            String ciudad_clientes = "Madrid";

            String llamada_contar = "{call contar_clientes_ciudad(?, ?)}";
            try (CallableStatement cs = conexion.prepareCall(llamada_contar)) {
                cs.setString(1, ciudad_clientes);
                cs.registerOutParameter(2, Types.INTEGER);
                cs.execute();
                int total_clientes = cs.getInt(2);
                System.out.println("\nClientes en " + ciudad_clientes + ": " + total_clientes);
            } // FIN PROCEDIMIENTO

        } catch (SQLException e) {
            System.out.println("Error de base de datos (" + e.getErrorCode() + "): " + e.getMessage());
            e.printStackTrace();
        }
    }
}