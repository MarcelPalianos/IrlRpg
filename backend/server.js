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
  res.json({
    message: 'Game started',
    player: {
      hp: 100,
      level: 1
    },
    enemy: {
      hp:100,
      level: 1
    },
    turn: 'player'

  });
});

app.listen(port, () => {
  console.log(`Backend running on http://localhost:${port}`);
});