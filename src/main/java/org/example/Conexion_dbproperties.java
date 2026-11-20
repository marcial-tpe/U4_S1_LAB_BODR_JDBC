package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Arrays;
import java.util.Properties;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.proxy.annotation.Pre;

public class Conexion_dbproperties {

    public static void main(String[] args) {
        // 1. Leer db.properties desde src/main/resources
        Properties datos = new Properties();
        try (InputStream in = Conexion_dbproperties.class.getResourceAsStream("/db.properties")) {
            if (in == null) {
                System.out.println("No se encuentra db.properties en src/main/resources");
                return;
            }
            datos.load(in);
        } catch (IOException e) {
            System.out.println("No se puede leer db.properties");
            e.printStackTrace();
            return;
        }

        String url = datos.getProperty("url");
        String usuario = datos.getProperty("usuario");
        String clave = datos.getProperty("clave");

        // 2. Conectar… y cerrar al terminar (try-with-resources)
        try (Connection conexion = DriverManager.getConnection(url, usuario, clave)) {
            System.out.println("Conectado a Oracle como " + conexion.getMetaData().getUserName());

           //3. Mostrar datos existentes para un id específico
            // nombre es char, direccion es objeto- llega como Struct y telefono es array (SQL:1999)
           int id= 102;
           String sql = "SELECT nombre, direccion, telefonos FROM cliente WHERE id = ?";

           try (PreparedStatement ps = conexion.prepareStatement(sql)) {
               ps.setInt(1,id);
               try (ResultSet rs = ps.executeQuery()){
                   if (rs.next()) {
                       Struct direccion = (Struct) rs.getObject("direccion");
                       Object[] atributos_direccion = direccion.getAttributes();
                       String calle = (String) atributos_direccion[0];
                       String ciudad = (String) atributos_direccion[1];
                       String cp = (String) atributos_direccion[2];

                       Array telefonos = rs.getArray("telefonos");
                       Object[] numeros_telefonos = (Object[]) telefonos.getArray();

                       System.out.println("Cliente: " + rs.getString("nombre"));
                       System.out.println("Calle: "+ calle);
                       System.out.println("Ciudad: "+ciudad);
                       //System.out.println("Telefonos" + Arrays.toString(numeros_telefonos));
                       for (int i=0; i< numeros_telefonos.length; i++){
                           System.out.println("Telefono " + (i+1) + ": " + numeros_telefonos[i]);
                       }
                       telefonos.free();
                   } else {
                        System.out.println("No existe el cliente: "+ id);
                   }
               }


           } //FIN SQL CONSULTA

            // 4. INSERTAR nuevo usuario
            int id_nuevo=103;
            String nombre_nuevo = "Autos Complutense";
            String calle_nueva = "Calle Libreros 26";
            String ciudad_nueva = "Alcala de Henares";
            String cp_nuevo = "28500";
            String[] telefonos_nuevos = {"918000010", "600111222"};

            Struct nueva_direccion = conexion.createStruct("DIRECCION_T", new Object[]{calle_nueva,ciudad_nueva,cp_nuevo});
            Array nuevos_telefonos = conexion.unwrap(OracleConnection.class).createOracleArray("TELEFONOS_T", telefonos_nuevos);

            String sql_nueva_direccion = "INSERT INTO cliente (ID, NOMBRE, DIRECCION, TELEFONOS) VALUES (?,?,?,?)";
            try (PreparedStatement ps = conexion.prepareStatement(sql_nueva_direccion)) {
                ps.setInt(1, id_nuevo);
                ps.setString(2, nombre_nuevo);
                ps.setObject(3, nueva_direccion);
                ps.setArray(4, nuevos_telefonos);
                ps.executeUpdate();
                System.out.println("Cliente :" + id_nuevo + "creado");
            } catch (SQLIntegrityConstraintViolationException e) {
                    System.out.println("El cliente " + id_nuevo + " ya existe");
            } finally {
                nuevos_telefonos.free();
            }

                        // 5. LEER TODOS los registros de la tabla
            String sql_todos = "SELECT id, nombre, direccion, telefonos FROM cliente ORDER BY id";
            try (PreparedStatement ps = conexion.prepareStatement(sql_todos);
                 ResultSet rs = ps.executeQuery()) {

                System.out.println("\n===== LISTADO DE CLIENTES =====");
                int total = 0;

                while (rs.next()) {
                    total++;
                    System.out.println("\nID: " + rs.getInt("id"));
                    System.out.println("Cliente: " + rs.getString("nombre"));

                    // Direccion (objeto -> Struct)
                    Struct dir = (Struct) rs.getObject("direccion");
                    if (dir != null) {
                        Object[] attr = dir.getAttributes();
                        System.out.println("Calle: "  + attr[0]);
                        System.out.println("Ciudad: " + attr[1]);
                        System.out.println("CP: "     + attr[2]);
                    } else {
                        System.out.println("Direccion: (sin datos)");
                    }

                    // Telefonos (VARRAY -> Array)
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

            // LLAMAR A UN PROCEDIMIENTO DE SQL
            String ciudad_clientes = "Madrid";
            String llamada_contar = "{call contar_clientes_ciudad (?,?)}";
            try (CallableStatement cs = conexion.prepareCall(llamada_contar)) {
                cs.setString(1,ciudad_clientes);
                cs.registerOutParameter(2, Types.INTEGER);
                cs.execute();
                int total_clientes = cs.getInt(2);
                System.out.println("Clientes en " + ciudad_clientes +": " + total_clientes);
            }


        } catch (SQLException e) {
            System.out.println("No se pudo conectar con Oracle");
            e.printStackTrace();
        }
    }
}