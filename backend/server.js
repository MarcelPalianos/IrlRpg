const games = {};
const express = require('express');
const app = express();
const port = 3000;

app.use(express.json());

// test endpoint
app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

// start game endpoint
app.post('/start-game', (req, res) => {
  const gameId = Date.now().toString();

  games[gameId] = {
      playerHp: 100,
      enemyHp: 100,
      turn: 'player'
  };

  res.json({
      gameId,
      player: { hp: 100 },
      enemy: { hp: 100 },
      turn: 'player'
  });
});
app.post('/make-move', (req, res) => {
  const { gameId } = req.body;
  const game = games[gameId];

  if (!game) {
      return res.status(404).json({ error: 'Game not found' });
  }

  if (game.battleOver) {
      return res.status(400).json({
          error: 'Battle already ended',
          battleOver: true,
          winner: game.winner,
          playerHp: game.playerHp,
          enemyHp: game.enemyHp,
          turn: game.turn
      });
  }

  if (game.turn !== 'player') {
      return res.status(400).json({ error: 'Not your turn' });
  }

  const playerDamage = Math.floor(Math.random() * 20) + 5;
  game.enemyHp -= playerDamage;
  if (game.enemyHp < 0) game.enemyHp = 0;

  let enemyDamage = 0;
  let winner = null;
  let battleOver = false;

  if (game.enemyHp <= 0) {
      battleOver = true;
      winner = 'player';
  } else {
      enemyDamage = Math.floor(Math.random() * 15) + 3;
      game.playerHp -= enemyDamage;
      if (game.playerHp < 0) game.playerHp = 0;

      if (game.playerHp <= 0) {
          battleOver = true;
          winner = 'enemy';
      }
  }

  game.battleOver = battleOver;
  game.winner = winner;
  game.turn = battleOver ? 'none' : 'player';

  res.json({
      playerHp: game.playerHp,
      enemyHp: game.enemyHp,
      turn: game.turn,
      playerDamage,
      enemyDamage,
      battleOver,
      winner
  });
});

app.listen(port, () => {
  console.log(`Backend running on http://localhost:${port}`);
});