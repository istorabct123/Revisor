-- Schema do banco Revisor - arquivos, materiais, questões, planejamentos

-- Matérias (ex: Direito Civil, Matemática)
CREATE TABLE IF NOT EXISTS materia (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nome TEXT NOT NULL,
    cor TEXT,
    created_at TEXT DEFAULT (datetime('now', 'localtime'))
);

-- Arquivos importados (PDFs, etc)
CREATE TABLE IF NOT EXISTS arquivo (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    materia_id INTEGER,
    caminho TEXT NOT NULL,
    nome_original TEXT NOT NULL,
    tamanho INTEGER,
    texto_extraido TEXT,
    questoes_extraidas INTEGER,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (materia_id) REFERENCES materia(id)
);

-- Questões
CREATE TABLE IF NOT EXISTS questao (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    materia_id INTEGER,
    assunto TEXT,
    enunciado TEXT NOT NULL,
    banca TEXT,
    ano INTEGER,
    tipo TEXT,
    alternativa_correta INTEGER,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (materia_id) REFERENCES materia(id)
);

-- Alternativas de cada questão
-- 'letra'   = A, B, C, D, E
-- 'correta' = 1 se é a alternativa correta, 0 caso contrário
CREATE TABLE IF NOT EXISTS alternativa (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    questao_id INTEGER NOT NULL,
    letra TEXT NOT NULL,
    texto TEXT NOT NULL,
    correta INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (questao_id) REFERENCES questao(id) ON DELETE CASCADE
);

-- Respostas do usuário (histórico de desempenho)
CREATE TABLE IF NOT EXISTS resposta (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    questao_id INTEGER NOT NULL,
    alternativa_escolhida INTEGER NOT NULL,
    acertou INTEGER NOT NULL,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (questao_id) REFERENCES questao(id)
);

-- Planejamento / tarefas
CREATE TABLE IF NOT EXISTS tarefa (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    materia_id INTEGER,
    titulo TEXT NOT NULL,
    concluida INTEGER DEFAULT 0,
    data_prevista TEXT,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (materia_id) REFERENCES materia(id)
);

-- Revisões programadas
CREATE TABLE IF NOT EXISTS revisao (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    questao_id INTEGER NOT NULL,
    proxima_revisao TEXT,
    created_at TEXT DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (questao_id) REFERENCES questao(id)
);
