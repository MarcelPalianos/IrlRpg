const express = require('express');
const app = express();
const port = 3000;

const games = {};

app.use(express.json());

// health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

// matchmaking join
app.post('/matchmaking/join', (req, res) => {
  let openGameId = null;

  for (const gameId in games) {
    if (games[gameId].status === 'waiting') {
      openGameId = gameId;
      break;
    }
  }

  if (openGameId) {
    games[openGameId].status = 'ready';
    games[openGameId].playersConnected = 2;

    return res.json({
      status: 'ok',
      gameId: openGameId,
      player: 'player2',
      gameStatus: 'ready'
    });
  }

  const gameId = Date.now().toString();

  games[gameId] = {
    status: 'waiting',
    player1: { hp: 100 },
    player2: { hp: 100 },
    playersConnected: 1,
    turn: 'player1',
    battleOver: false,
    winner: null
  };

  res.json({
    status: 'ok',
    gameId,
    player: 'player1',
    gameStatus: 'waiting'
  });
});

// get game state
app.get('/game-state/:gameId', (req, res) => {
  const { gameId } = req.params;
  const game = games[gameId];

  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }

  res.json(game);
});

// make move
app.post('/make-move', (req, res) => {
  const { gameId, player } = req.body;
  const game = games[gameId];

  if (!game) {
    return res.status(404).json({ error: 'Game not found' });
  }

  if (game.status !== 'ready') {
    return res.status(400).json({ error: 'Game is not ready yet' });
  }

  if (game.battleOver) {
    return res.status(400).json({
      error: 'Battle already ended',
      battleOver: true,
      winner: game.winner
    });
  }

  if (game.turn !== player) {
    return res.status(400).json({ error: 'Not your turn' });
  }

  const attacker = player;
  const defender = player === 'player1' ? 'player2' : 'player1';

  const damage = Math.floor(Math.random() * 20) + 5;
  game[defender].hp -= damage;
  if (game[defender].hp < 0) game[defender].hp = 0;

  if (game[defender].hp <= 0) {
    game.battleOver = true;
    game.winner = attacker;
    game.turn = 'none';
  } else {
    game.turn = defender;
  }

  res.json({
    status: 'ok',
    gameId,
    player1Hp: game.player1.hp,
    player2Hp: game.player2.hp,
    turn: game.turn,
    damage,
    battleOver: game.battleOver,
    winner: game.winner
  });
});

app.listen(port, () => {
  console.log(`Backend running on http://localhost:${port}`);
});