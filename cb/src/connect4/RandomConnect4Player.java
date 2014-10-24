package connect4;
import game.*;

public class RandomConnect4Player extends BaseConnect4Player {
	private int [] possibilities = new int [Connect4State.NUM_COLS];
	private int numPoss;
	
	public RandomConnect4Player(String n)
	{
		super(n, false);
	}
	public GameMove getMove(GameState st, String lastMove)
	{
		Connect4State board = (Connect4State)st;
		numPoss = 0;
		for (int c=0; c<Connect4State.NUM_COLS; c++) {
			if (board.numInCol[c] < Connect4State.NUM_ROWS) {
				possibilities[numPoss++] = c;
			}
		}
		if (numPoss == 0) {
			return null;
		} else {
			int which = (int)(Math.random() * numPoss);
			return new Connect4Move(possibilities[which]);
		}
	}
	public static void main(String[] args) {
		GamePlayer p = new RandomConnect4Player("C4 randomizer");
		p.compete(args);
	}

}
