package Common;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.GridPane;

public class GUI {
    public static GridPane getGridPane() {
        GridPane gridPane = new GridPane();

        //Setting size for the pane
        gridPane.setMinSize(600, 750);

        //Setting the padding
        gridPane.setPadding(new Insets(10, 10, 10, 10));

        //Setting the vertical and horizontal gaps between the columns
        gridPane.setVgap(5);
        gridPane.setHgap(5);

        //Setting the Grid alignment
        gridPane.setAlignment(Pos.CENTER);

        return gridPane;
    }
}
