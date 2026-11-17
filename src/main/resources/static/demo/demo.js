/*
 * Modo demonstração do Swagger UI (/demo/index.html).
 * - Guarda o id retornado ao criar professor, aluno, aluna, disciplina e matrícula.
 * - Troca os marcadores COLE-AQUI-... das requisições seguintes (corpo e URL) por esses ids.
 * - Após cada login bem-sucedido, autoriza o Swagger com o access token recebido.
 * Os ids ficam no localStorage do navegador apenas para a apresentação; "Limpar ids" os remove.
 */
(function (raiz) {
  'use strict';

  var CHAVE = 'campusApiDemoIds';
  var MARCADORES = {
    'COLE-AQUI-O-ID-DO-PROFESSOR': 'professor',
    'COLE-AQUI-O-ID-DO-ALUNO': 'aluno',
    'COLE-AQUI-O-ID-DA-ALUNA': 'aluna',
    'COLE-AQUI-O-ID-DA-DISCIPLINA': 'disciplina',
    'COLE-AQUI-O-ID-DA-MATRICULA': 'matricula'
  };
  // Matrícula (número) do exemplo "Mariana Costa": identifica a segunda aluna.
  var MATRICULA_ALUNA = '2026002';

  function substituir(texto, ids) {
    if (typeof texto !== 'string') return texto;
    Object.keys(MARCADORES).forEach(function (marcador) {
      var id = ids[MARCADORES[marcador]];
      if (id) texto = texto.split(marcador).join(id);
    });
    return texto;
  }

  function caminho(url) {
    try { return new URL(url, 'http://localhost').pathname; } catch (e) { return ''; }
  }

  function corpo(res) {
    if (res.body && typeof res.body === 'object') return res.body;
    try { return JSON.parse(res.text || res.data || ''); } catch (e) { return null; }
  }

  /** Atualiza os ids a partir de uma resposta; devolve o access token quando a resposta for de login. */
  function registrar(res, ids) {
    var dados = corpo(res);
    if (!dados) return null;
    var p = caminho(res.url);
    if (res.status === 200 && p === '/auth/login' && dados.accessToken) return dados.accessToken;
    if (res.status !== 201 || !dados.id) return null;
    if (p === '/professores') ids.professor = dados.id;
    else if (p === '/alunos') ids[dados.matricula === MATRICULA_ALUNA ? 'aluna' : 'aluno'] = dados.id;
    else if (p === '/disciplinas') ids.disciplina = dados.id;
    else if (p === '/matriculas' || /^\/disciplinas\/[^/]+\/alunos\/[^/]+$/.test(p)) ids.matricula = dados.id;
    return null;
  }

  function carregar(armazenamento) {
    try { return JSON.parse(armazenamento.getItem(CHAVE) || '{}'); } catch (e) { return {}; }
  }

  function salvar(armazenamento, ids) {
    try { armazenamento.setItem(CHAVE, JSON.stringify(ids)); } catch (e) { /* armazenamento indisponível */ }
  }

  var api = { substituir: substituir, registrar: registrar, carregar: carregar, salvar: salvar, CHAVE: CHAVE };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  raiz.CampusDemo = api;

  if (typeof raiz.SwaggerUIBundle !== 'function' || typeof document === 'undefined') return;

  var armazenamento = raiz.localStorage;
  var ids = carregar(armazenamento);

  function mostrarIds() {
    var painel = document.getElementById('ids');
    if (!painel) return;
    var nomes = Object.keys(ids);
    painel.textContent = nomes.length
      ? 'Ids capturados: ' + nomes.map(function (n) { return n + ' ' + ids[n].slice(0, 8) + '…'; }).join(' · ')
      : 'Nenhum id capturado ainda: crie professor, alunos, disciplina e matrícula pelos exemplos.';
  }

  raiz.ui = raiz.SwaggerUIBundle({
    url: '/v3/api-docs',
    dom_id: '#swagger-ui',
    deepLinking: true,
    tryItOutEnabled: true,
    persistAuthorization: true,
    displayRequestDuration: true,
    defaultModelsExpandDepth: 0,
    presets: [raiz.SwaggerUIBundle.presets.apis],
    layout: 'BaseLayout',
    requestInterceptor: function (req) {
      req.url = substituir(req.url, ids);
      req.body = substituir(req.body, ids);
      return req;
    },
    responseInterceptor: function (res) {
      var token = registrar(res, ids);
      salvar(armazenamento, ids);
      mostrarIds();
      if (token && raiz.ui) {
        raiz.ui.authActions.authorize({
          bearerAuth: { name: 'bearerAuth', schema: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' }, value: token }
        });
      }
      return res;
    }
  });

  document.getElementById('limpar').addEventListener('click', function () {
    ids = {};
    salvar(armazenamento, ids);
    if (raiz.ui) raiz.ui.authActions.logout(['bearerAuth']);
    mostrarIds();
  });
  mostrarIds();
})(typeof window !== 'undefined' ? window : globalThis);
