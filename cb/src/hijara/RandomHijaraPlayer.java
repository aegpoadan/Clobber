package hijara;
import game.*;
import java.util.*;

public class RandomHijaraPlayer extends GamePlayer {
	public RandomHijaraPlayer(String n) 
	{
		super(n, new HijaraState(), false);
	}
	public GameMove getMove(GameState state, String lastMove)
	{
		HijaraState board = (HijaraState)state;
		ArrayList<HijaraMove> list = new ArrayList<HijaraMove>();  
		HijaraMove mv = new HijaraMove();
		for (int r=0; r<HijaraState.ROWS; r++) {
			for (int c=0; c<HijaraState.COLS; c++) {
				mv.row = r;
				mv.col = c;
				if (board.moveOK(mv)) {
					list.add((HijaraMove)mv.clone());
				}
			}
		}
		int which = Util.randInt(0, list.size()-1);
		return list.get(which);
	}
	public static void main(String [] args)
	{
		GamePlayer p = new RandomHijaraPlayer("Random+");
		p.compete(args, 1);
	}
}
