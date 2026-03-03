package dev.revisor.revisor.model;

/**
 * Representa uma questão do banco de questões.
 */
public class Questao {

    private final Integer id;
    private final Integer materiaId;
    private final String materiaNome;
    private final String assunto;
    private final String enunciado;
    private final String banca;
    private final Integer ano;
    private final String tipo;
    private final Integer alternativaCorreta;

    public Questao(Integer id,
                   Integer materiaId,
                   String materiaNome,
                   String assunto,
                   String enunciado,
                   String banca,
                   Integer ano,
                   String tipo,
                   Integer alternativaCorreta) {
        this.id = id;
        this.materiaId = materiaId;
        this.materiaNome = materiaNome;
        this.assunto = assunto;
        this.enunciado = enunciado;
        this.banca = banca;
        this.ano = ano;
        this.tipo = tipo;
        this.alternativaCorreta = alternativaCorreta;
    }

    public Integer getId() {
        return id;
    }

    public Integer getMateriaId() {
        return materiaId;
    }

    public String getMateriaNome() {
        return materiaNome;
    }

    public String getAssunto() {
        return assunto;
    }

    public String getEnunciado() {
        return enunciado;
    }

    public String getBanca() {
        return banca;
    }

    public Integer getAno() {
        return ano;
    }

    public String getTipo() {
        return tipo;
    }

    public Integer getAlternativaCorreta() {
        return alternativaCorreta;
    }
}

