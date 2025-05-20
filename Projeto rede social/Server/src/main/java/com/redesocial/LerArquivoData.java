package com.redesocial;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.concurrent.ConcurrentHashMap;

public class LerArquivoData {
    public static void main(String[] args) {
        // Caminho para o arquivo .data
//        String dataFilePath = "C:/Users/samir/git/RedeSocial/Projeto rede social/Server/server1_data/users/users_server1.data";
//        String dataFilePath = "C:/Users/samir/git/RedeSocial/Projeto rede social/Server/server2_data/users/users_server2.data";
        String dataFilePath = "C:/Users/samir/git/RedeSocial/Projeto rede social/Server/server3_data/users/users_server3.data";

        File file = new File(dataFilePath);
        if (!file.exists()) {
            System.out.println("Arquivo de usuários não encontrado");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            // Desserialização do objeto
            Object obj = ois.readObject();

            // Verificar se é um ConcurrentHashMap
            if (obj instanceof ConcurrentHashMap) {
                ConcurrentHashMap<String, Object> userMap = (ConcurrentHashMap<String, Object>) obj;

                System.out.println("Carregados " + userMap.size() + " usuários do arquivo");

                // Listar as chaves (usuários)
                System.out.println("\nLista de usuários:");
                for (String username : userMap.keySet()) {
                    System.out.println("- " + username);
                }

            } else {
                System.out.println("O arquivo não contém um ConcurrentHashMap como esperado.");
                System.out.println("Tipo real: " + obj.getClass().getName());
            }

        } catch (Exception e) {
            System.out.println("Erro ao carregar usuários do arquivo");
            e.printStackTrace();
        }
    }
}