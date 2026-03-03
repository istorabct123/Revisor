package dev.revisor.revisor.model;

import java.time.LocalDateTime;

/**
 * Representa um arquivo importado (normalmente um PDF de material de estudo).
 */
public class Arquivo {

    private Integer id;
    private Integer materiaId;      // pode ser nulo se não estiver vinculado a uma matéria específica
    private String caminho;        // caminho absoluto onde o arquivo foi salvo
    private String nomeOriginal;   // nome do arquivo na importação
    private Long tamanho;          // em bytes
    private LocalDateTime createdAt;

    public Arquivo(Integer id, Integer materiaId, String caminho, String nomeOriginal, Long tamanho, LocalDateTime createdAt) {
        this.id = id;
        this.materiaId = materiaId;
        this.caminho = caminho;
        this.nomeOriginal = nomeOriginal;
        this.tamanho = tamanho;
        this.createdAt = createdAt;
    }

    public Integer getId() {
        return id;
    }

    public Integer getMateriaId() {
        return materiaId;
    }

    public String getCaminho() {
        return caminho;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public Long getTamanho() {
        return tamanho;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

