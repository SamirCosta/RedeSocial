package com.redesocial;

import com.redesocial.model.Post;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class LerPost {
    public static List<Post> lerPostsDoArquivo(String fileName) {
        List<Post> posts = new ArrayList<>();

        try (FileInputStream fileIn = new FileInputStream(fileName);
             ObjectInputStream objectIn = new ObjectInputStream(fileIn)) {

            // Lê o ConcurrentHashMap do arquivo
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, Post> mapaPost = (ConcurrentHashMap<String, Post>) objectIn.readObject();

            // Converte os valores do mapa para uma lista
            posts.addAll(mapaPost.values());

            System.out.println("Número de posts encontrados: " + mapaPost.size());

        } catch (FileNotFoundException e) {
            System.err.println("Arquivo não encontrado: " + fileName);
        } catch (IOException e) {
            System.err.println("Erro ao ler arquivo: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Classe não encontrada: " + e.getMessage());
        } catch (ClassCastException e) {
            System.err.println("Erro de conversão: " + e.getMessage());
            System.err.println("O arquivo não contém o tipo de dados esperado.");
        }

        return posts;
    }

    public static ConcurrentHashMap<String, Post> lerMapaPostsDoArquivo(String fileName) {
        try (FileInputStream fileIn = new FileInputStream(fileName);
             ObjectInputStream objectIn = new ObjectInputStream(fileIn)) {

            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, Post> mapaPost = (ConcurrentHashMap<String, Post>) objectIn.readObject();

            return mapaPost;

        } catch (FileNotFoundException e) {
            System.err.println("Arquivo não encontrado: " + fileName);
        } catch (IOException e) {
            System.err.println("Erro ao ler arquivo: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Classe não encontrada: " + e.getMessage());
        } catch (ClassCastException e) {
            System.err.println("Erro de conversão: " + e.getMessage());
        }

        return null;
    }

    public static Post buscarPostPorId(String fileName, String postId) {
        ConcurrentHashMap<String, Post> mapa = lerMapaPostsDoArquivo(fileName);
        if (mapa != null) {
            return mapa.get(postId);
        }
        return null;
    }

    public static void exibirInfoArquivo(String fileName) {
        File arquivo = new File(fileName);
        if (arquivo.exists()) {
            System.out.println("Arquivo: " + fileName);
            System.out.println("Tamanho: " + arquivo.length() + " bytes");
            System.out.println("Última modificação: " + new java.util.Date(arquivo.lastModified()));
        } else {
            System.out.println("Arquivo não encontrado: " + fileName);
        }
    }

    // Método principal para teste
    public static void main(String[] args) {
        String nomeArquivo = "C:/Users/samir/git/RedeSocial/Projeto rede social/Server/post_data/posts_server3.data";

        System.out.println("=== Informações do Arquivo ===");
        exibirInfoArquivo(nomeArquivo);
        System.out.println();

        System.out.println("=== Lendo Posts do Arquivo ===");
        List<Post> posts = lerPostsDoArquivo(nomeArquivo);

        if (posts.isEmpty()) {
            System.out.println("Nenhum post encontrado no arquivo.");
        } else {
            System.out.println("Foram encontrados " + posts.size() + " post(s):");
            System.out.println();

            for (int i = 0; i < posts.size(); i++) {
                System.out.println("Post " + (i + 1) + ":");
                System.out.println(posts.get(i));
                System.out.println("----------------------------------------");
            }
        }

        // Exemplo: ler o mapa diretamente
        System.out.println("\n=== Lendo Mapa Diretamente ===");
        ConcurrentHashMap<String, Post> mapa = lerMapaPostsDoArquivo(nomeArquivo);
        if (mapa != null) {
            System.out.println("Mapa carregado com " + mapa.size() + " posts");
            System.out.println("Chaves do mapa: " + mapa.keySet());

            // Exemplo de busca por ID (se você souber um ID específico)
            // Post postEspecifico = buscarPostPorId(nomeArquivo, "algum_id");
        }
    }
}