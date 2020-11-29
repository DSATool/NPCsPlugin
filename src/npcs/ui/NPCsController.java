/*
 * Copyright 2017 DSATool team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package npcs.ui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import dsa41basis.fight.AttackTable;
import dsa41basis.hero.Attribute;
import dsa41basis.hero.Energy;
import dsa41basis.ui.hero.BasicValuesController;
import dsa41basis.ui.hero.BasicValuesController.CharacterType;
import dsa41basis.util.HeroUtil;
import dsatool.resources.ResourceManager;
import dsatool.ui.ReactiveSpinner;
import dsatool.util.ErrorLogger;
import dsatool.util.Tuple;
import dsatool.util.Util;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import jsonant.event.JSONListener;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class NPCsController {
	@FXML
	private BorderPane pane;
	@FXML
	private VBox box;
	@FXML
	private TreeView<Tuple<String, JSONObject>> list;
	@FXML
	private TreeItem<Tuple<String, JSONObject>> root;
	@FXML
	private TextField name;
	@FXML
	private ReactiveSpinner<Integer> lep;
	@FXML
	private ReactiveSpinner<Integer> lepMax;
	@FXML
	private ReactiveSpinner<Integer> aup;
	@FXML
	private ReactiveSpinner<Integer> aupMax;
	@FXML
	private ReactiveSpinner<Integer> asp;
	@FXML
	private ReactiveSpinner<Integer> aspMax;
	@FXML
	private ReactiveSpinner<Integer> kap;
	@FXML
	private ReactiveSpinner<Integer> kapMax;
	@FXML
	private ReactiveSpinner<Integer> ko;
	@FXML
	private ReactiveSpinner<Integer> ws;
	@FXML
	private CheckBox wsBonus;
	@FXML
	private CheckBox wsMalus;
	@FXML
	private CheckBox tough;
	@FXML
	private TextField newAttackField;

	private Map<JSONObject, TreeItem<Tuple<String, JSONObject>>> npcs = new HashMap<>();

	private final AttackTable attacksTable;
	private final BasicValuesController basicValuesController;

	private JSONObject actualNPC;
	private boolean update = false;

	private TreeItem<Tuple<String, JSONObject>> draggedItem;

	private final JSONListener npcListener = o -> {
		setData();
	};

	public NPCsController() {
		final FXMLLoader fxmlLoader = new FXMLLoader();

		fxmlLoader.setController(this);

		try {
			fxmlLoader.load(getClass().getResource("NPCsController.fxml").openStream());
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		basicValuesController = new BasicValuesController(new SimpleBooleanProperty(false), CharacterType.NORMAL);
		box.getChildren().add(basicValuesController.getControl());

		final StringConverter<Tuple<String, JSONObject>> converter = new StringConverter<>() {
			@Override
			public Tuple<String, JSONObject> fromString(final String newName) {
				return new Tuple<>(newName, null);
			}

			@Override
			public String toString(final Tuple<String, JSONObject> item) {
				final JSONObject npc = item._2;
				if (npc != null)
					return getName(npc);
				else
					return item._1;
			}
		};

		list.setCellFactory(tv -> {
			final TreeCell<Tuple<String, JSONObject>> cell = new TextFieldTreeCell<>(converter) {
				@Override
				public void commitEdit(final Tuple<String, JSONObject> newName) {
					final Tuple<String, JSONObject> item = getItem();
					if (item._1 != null) {
						super.commitEdit(newName);
					} else {
						setName(item._2, newName._1);
					}
				}
			};

			cell.setOnDragDetected(event -> {
				draggedItem = cell.getTreeItem();

				final Dragboard dragBoard = cell.startDragAndDrop(TransferMode.MOVE);
				final ClipboardContent content = new ClipboardContent();
				content.put(DataFormat.PLAIN_TEXT, 0);
				dragBoard.setContent(content);
				event.consume();
			});

			cell.setOnDragOver(e -> {
				TreeItem<Tuple<String, JSONObject>> target = cell.getTreeItem();
				while (target != null) {
					if (target == draggedItem) return;
					target = target.getParent();
				}
				e.acceptTransferModes(TransferMode.MOVE);
			});

			cell.setOnDragDropped(event -> {
				final Dragboard dragBoard = event.getDragboard();
				if (!dragBoard.hasContent(DataFormat.PLAIN_TEXT)) return;

				final JSONObject npc = draggedItem.getValue()._2;
				if (npc != null) {
					final JSONObject bio = npc.getObj("Biografie");
					bio.put("Gruppe", getGroup(cell.getTreeItem()).clone(bio));
				} else {
					final TreeItem<Tuple<String, JSONObject>> target = cell.getTreeItem();
					final JSONArray oldGroup = getGroup(draggedItem.getParent());
					final JSONArray newGroup = getGroup(target);
					draggedItem.getParent().getChildren().remove(draggedItem);
					if (target == null) {
						root.getChildren().add(draggedItem);
					} else if (target.getValue()._2 != null) {
						target.getParent().getChildren().add(draggedItem);
					} else {
						target.getChildren().add(draggedItem);
					}
					moveNPCs(draggedItem, oldGroup, newGroup);
				}

				reload();
				event.setDropCompleted(true);
			});

			final ContextMenu menu = new ContextMenu();

			final MenuItem addItem = new MenuItem("Neuer NSC");
			addItem.setOnAction(event -> addNewNPC(cell.getTreeItem()));

			final MenuItem addGroupItem = new MenuItem("Neue Gruppe");
			addGroupItem.setOnAction(event -> addNewGroup(cell.getTreeItem()));

			final MenuItem removeItem = new MenuItem("Löschen");
			removeItem.setOnAction(event -> {
				final Tuple<String, JSONObject> item = cell.getItem();
				if (item._2 != null) {
					removeNPC(item._2);
				} else {
					removeGroup(cell.getTreeItem());
				}
			});
			removeItem.visibleProperty().bind(cell.emptyProperty().not());

			menu.getItems().addAll(addItem, addGroupItem, removeItem);
			cell.setContextMenu(menu);

			return cell;
		});

		final MultipleSelectionModel<TreeItem<Tuple<String, JSONObject>>> listModel = list.getSelectionModel();
		listModel.selectedItemProperty().addListener((o, oldV, newV) -> {
			if (oldV != newV && newV != null && newV.getValue()._2 != null) {
				setNPC(newV.getValue()._2);
			}
		});

		final BooleanBinding isEditable = Bindings.createBooleanBinding(() -> {
			final TreeItem<Tuple<String, JSONObject>> item = list.getSelectionModel().getSelectedItem();
			return item != null && item.getValue()._2 != null;
		},
				list.getSelectionModel().selectedItemProperty());

		box.visibleProperty().bind(isEditable);

		attacksTable = new AttackTable(isEditable, box.widthProperty(), false);
		final Node tableControl = attacksTable.getControl();
		box.getChildren().add(tableControl);
		GridPane.setRowIndex(tableControl, 4);
		GridPane.setColumnSpan(tableControl, 6);

		registerUIListeners();

		reload();

		ResourceManager.addPathListener("npcs/", (discard) -> {
			if (!discard) {
				reload();
			}
		});
	}

	private void addNewGroup(final TreeItem<Tuple<String, JSONObject>> treeItem) {
		final TreeItem<Tuple<String, JSONObject>> parent;
		if (treeItem != null) {
			parent = treeItem.getValue()._1 != null ? treeItem : treeItem.getParent();
		} else {
			parent = root;
		}
		final ObservableList<TreeItem<Tuple<String, JSONObject>>> siblings = parent.getChildren();
		final String[] groupName = { "Neue Gruppe" };
		int counter = 1;
		while (siblings.stream().anyMatch(item -> groupName[0].equals(item.getValue()._1))) {
			groupName[0] = "Neue Gruppe " + counter;
			++counter;
		}
		final TreeItem<Tuple<String, JSONObject>> newItem = new TreeItem<>(new Tuple<>(groupName[0], null));
		siblings.add(newItem);
		list.getSelectionModel().select(newItem);
	}

	private void addNewNPC(final TreeItem<Tuple<String, JSONObject>> base) {
		final JSONObject npc = ResourceManager.getNewResource("npcs/Unbenannter_NSC");
		final JSONObject bio = npc.getObj("Biografie");
		bio.put("Gruppe", getGroup(base).clone(bio));
		final TreeItem<Tuple<String, JSONObject>> item = npcs.get(npc);
		item.getParent().getChildren().remove(item);
		final TreeItem<Tuple<String, JSONObject>> newItem = addNPC(npc);
		npcs.put(npc, newItem);
		list.getSelectionModel().select(newItem);
	}

	private TreeItem<Tuple<String, JSONObject>> addNPC(final JSONObject npc) {
		final JSONArray groups = npc.getObj("Biografie").getArr("Gruppe");
		TreeItem<Tuple<String, JSONObject>> current = root;
		for (final String groupName : groups.getStrings()) {
			final TreeItem<Tuple<String, JSONObject>> group = new TreeItem<>(new Tuple<>(groupName, null));
			final ObservableList<TreeItem<Tuple<String, JSONObject>>> children = current.getChildren();
			final Optional<TreeItem<Tuple<String, JSONObject>>> groupItem = children.stream().filter(item -> groupName.equals(item.getValue()._1)).findFirst();
			if (groupItem.isPresent()) {
				current = groupItem.get();
			} else {
				current = group;
				children.add(group);
			}
		}
		final TreeItem<Tuple<String, JSONObject>> item = new TreeItem<>(new Tuple<>(null, npc));
		current.getChildren().add(item);
		return item;
	}

	private boolean containsNPCs(final TreeItem<Tuple<String, JSONObject>> item) {
		return item.getChildren().stream().anyMatch(i -> i.getValue()._2 != null || containsNPCs(i));
	}

	private JSONArray getGroup(TreeItem<Tuple<String, JSONObject>> item) {
		final JSONArray groups = new JSONArray(null);
		while (item != null) {
			final Tuple<String, JSONObject> value = item.getValue();
			if (value != null && value._1 != null) {
				groups.add(0, value._1);
			}
			item = item.getParent();
		}
		return groups;
	}

	private String getName(final JSONObject npc) {
		return npc.getObj("Biografie").getStringOrDefault("Name", "Unbenannter NSC");
	}

	public Node getRoot() {
		return pane;
	}

	private void moveNPCs(final TreeItem<Tuple<String, JSONObject>> treeItem, final JSONArray oldGroup, final JSONArray newGroup) {
		for (final TreeItem<Tuple<String, JSONObject>> child : treeItem.getChildren()) {
			final Tuple<String, JSONObject> value = child.getValue();
			if (value._2 != null) {
				final JSONObject bio = value._2.getObj("Biografie");
				final JSONArray group = bio.getArr("Gruppe");
				for (int i = 0; i < oldGroup.size(); ++i) {
					group.removeAt(0);
				}
				for (int i = 0; i < newGroup.size(); ++i) {
					group.add(i, newGroup.getString(i));
				}
			} else {
				moveNPCs(child, oldGroup, newGroup);
			}
		}
	}

	private void registerUIListeners() {
		final BooleanSupplier check = () -> update || actualNPC == null;

		name.setOnAction(e -> setName(actualNPC, name.getText()));
		name.focusedProperty().addListener(Util.changeListener(check, newV -> setName(actualNPC, name.getText())));

		lep.valueProperty().addListener(Util.changeListener(check, newV -> {
			final Energy lepEnergy = new Energy("Lebensenergie", new JSONObject(null), actualNPC);
			lepEnergy.setManualModifier(lep.getValue() - lepMax.getValue());
		}));
		lepMax.valueProperty().addListener(Util.changeListener(check, newV -> {
			final Energy lepEnergy = new Energy("Lebensenergie", new JSONObject(null), actualNPC);
			lepEnergy.setPermanent(lepMax.getValue());
		}));

		aup.valueProperty().addListener(Util.changeListener(check, newV -> {
			final Energy aupEnergy = new Energy("Ausdauer", new JSONObject(null), actualNPC);
			aupEnergy.setManualModifier(aup.getValue() - aupMax.getValue());
		}));
		aupMax.valueProperty().addListener(Util.changeListener(check, newV -> {
			final Energy aupEnergy = new Energy("Ausdauer", new JSONObject(null), actualNPC);
			aupEnergy.setPermanent(aupMax.getValue());
		}));

		asp.valueProperty().addListener(Util.changeListener(check, newV -> {
			if (asp.getValue() != 0 || aspMax.getValue() != 0) {
				final Energy aspEnergy = new Energy("Astralenergie", new JSONObject(null), actualNPC);
				aspEnergy.setManualModifier(asp.getValue() - aspMax.getValue());
			} else {
				final JSONObject basicValues = actualNPC.getObj("Basiswerte");
				basicValues.remove("Astralenergie");
				basicValues.notifyListeners(npcListener);
			}
		}));
		aspMax.valueProperty().addListener(Util.changeListener(check, newV -> {
			if (asp.getValue() != 0 || aspMax.getValue() != 0) {
				final Energy aspEnergy = new Energy("Astralenergie", new JSONObject(null), actualNPC);
				aspEnergy.setPermanent(aspMax.getValue());
			} else {
				final JSONObject basicValues = actualNPC.getObj("Basiswerte");
				basicValues.remove("Astralenergie");
				basicValues.notifyListeners(npcListener);
			}
		}));

		kap.valueProperty().addListener(Util.changeListener(check, newV -> {
			if (kap.getValue() != 0 || kapMax.getValue() != 0) {
				final Energy kapEnergy = new Energy("Karmaenergie", new JSONObject(null), actualNPC);
				kapEnergy.setManualModifier(kap.getValue() - kapMax.getValue());
			} else {
				final JSONObject basicValues = actualNPC.getObj("Basiswerte");
				basicValues.remove("Karmaenergie");
				basicValues.notifyListeners(npcListener);
			}
		}));
		kapMax.valueProperty().addListener(Util.changeListener(check, newV -> {
			if (kap.getValue() != 0 || kapMax.getValue() != 0) {
				final Energy kapEnergy = new Energy("Karmaenergie", new JSONObject(null), actualNPC);
				kapEnergy.setPermanent(kapMax.getValue());
			} else {
				final JSONObject basicValues = actualNPC.getObj("Basiswerte");
				basicValues.remove("Karmaenergie");
				basicValues.notifyListeners(npcListener);
			}
		}));

		ko.valueProperty().addListener(Util.changeListener(check, newV -> {
			final Attribute koAttribute = new Attribute("Konstitution", actualNPC.getObj("Eigenschaften").getObj("KO"));
			koAttribute.setValue(newV);
		}));

		ws.valueProperty().addListener(Util.changeListener(check, newV -> {
			final int maxKO = (newV - actualNPC.getObj("Basiswerte").getObj("Wundschwelle").getIntOrDefault("Modifikator", 0)) * 2;
			if (ko.getValue() > maxKO) {
				ko.getValueFactory().setValue(maxKO);
			} else if (ko.getValue() < maxKO - 1) {
				ko.getValueFactory().setValue(maxKO - 1);
			}
		}));

		wsBonus.selectedProperty().addListener(Util.changeListener(check, newV -> {
			final JSONObject actual = actualNPC.getObj("Vorteile");
			final JSONObject pro = HeroUtil.findProConOrSkill("Eisern")._1;
			if (newV) {
				final JSONObject actualPro = new JSONObject(actual);
				actual.put("Eisern", actualPro);
				HeroUtil.applyEffect(actualNPC, "Eisern", pro, actualPro);
				wsMalus.setSelected(false);
			} else {
				final JSONObject actualPro = actual.getObj("Eisern");
				actual.removeKey("Eisern");
				HeroUtil.unapplyEffect(actualNPC, "Eisern", pro, actualPro);
			}
			actual.notifyListeners(null);
		}));

		wsMalus.selectedProperty().addListener(Util.changeListener(check, newV -> {
			final JSONObject actual = actualNPC.getObj("Nachteile");
			final JSONObject con = HeroUtil.findProConOrSkill("Glasknochen")._1;
			if (newV) {
				final JSONObject actualCon = new JSONObject(actual);
				actual.put("Glasknochen", actualCon);
				HeroUtil.applyEffect(actualNPC, "Glasknochen", con, actualCon);
				wsBonus.setSelected(false);
			} else {
				final JSONObject actualCon = actual.getObj("Glasknochen");
				actual.removeKey("Glasknochen");
				HeroUtil.unapplyEffect(actualNPC, "Glasknochen", con, actualCon);
			}
			actual.notifyListeners(null);
		}));

		tough.selectedProperty().addListener(Util.changeListener(check, newV -> {
			final JSONObject actual = actualNPC.getObj("Vorteile");
			final JSONObject pro = HeroUtil.findProConOrSkill("Zäher Hund")._1;
			if (newV) {
				final JSONObject actualPro = new JSONObject(actual);
				actual.put("Zäher Hund", actualPro);
				HeroUtil.applyEffect(actualNPC, "Zäher Hund", pro, actualPro);
			} else {
				final JSONObject actualPro = actual.getObj("Zäher Hund");
				actual.removeKey("Zäher Hund");
				HeroUtil.unapplyEffect(actualNPC, "Zäher Hund", pro, actualPro);
			}
			actual.notifyListeners(null);
		}));
	}

	private void reload() {
		if (update) return;

		final MultipleSelectionModel<TreeItem<Tuple<String, JSONObject>>> listModel = list.getSelectionModel();
		final TreeItem<Tuple<String, JSONObject>> selected = listModel.getSelectedItem();
		final JSONObject selectedNPC = selected != null ? selected.getValue()._2 : null;
		final List<JSONObject> theNPCs = ResourceManager.getAllResources("npcs/");

		final Map<JSONObject, TreeItem<Tuple<String, JSONObject>>> newNPCs = new HashMap<>();
		for (final JSONObject npc : theNPCs) {
			if (npcs.containsKey(npc)) {
				final TreeItem<Tuple<String, JSONObject>> item = npcs.get(npc);
				final JSONArray groups = npc.getObj("Biografie").getArr("Gruppe");
				if (!getGroup(item).equals(groups)) {
					item.getParent().getChildren().remove(item);
					newNPCs.put(npc, addNPC(npc));
				} else {
					newNPCs.put(npc, item);
				}
			} else {
				newNPCs.put(npc, addNPC(npc));
			}
		}
		for (final JSONObject npc : npcs.keySet()) {
			if (!newNPCs.containsKey(npc)) {
				final TreeItem<Tuple<String, JSONObject>> item = npcs.get(npc);
				final TreeItem<Tuple<String, JSONObject>> parent = item.getParent();
				if (parent != null) {
					parent.getChildren().remove(item);
				}
			}
		}

		npcs = newNPCs;
		if (npcs.isEmpty()) {
			listModel.clearSelection();
		} else {
			final TreeItem<Tuple<String, JSONObject>> item = npcs.get(selectedNPC);
			if (item != null) {
				listModel.clearSelection();
				listModel.select(item);
			}
		}
	}

	private void removeGroup(final TreeItem<Tuple<String, JSONObject>> item) {
		if (containsNPCs(item)) {
			final Alert deleteConfirmation = new Alert(AlertType.CONFIRMATION);
			deleteConfirmation.setTitle("Gruppe löschen?");
			deleteConfirmation.setHeaderText("Gruppe " + item.getValue()._1 + " löschen?");
			deleteConfirmation.setContentText("Die NSCs können danach nicht wiederhergestellt werden!");
			deleteConfirmation.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

			final Optional<ButtonType> result = deleteConfirmation.showAndWait();
			if (result.isPresent() && result.get().equals(ButtonType.YES)) {
				removeGroupInternal(item);
			}
		} else {
			removeGroupInternal(item);
		}
	}

	private void removeGroupInternal(final TreeItem<Tuple<String, JSONObject>> item) {
		update = true;
		final Iterator<TreeItem<Tuple<String, JSONObject>>> children = item.getChildren().iterator();
		TreeItem<Tuple<String, JSONObject>> child;
		while (children.hasNext()) {
			child = children.next();
			children.remove();
			final JSONObject npc = child.getValue()._2;
			if (npc != null) {
				ResourceManager.deleteResource(npc);
			} else {
				removeGroupInternal(child);
			}
		}
		final TreeItem<Tuple<String, JSONObject>> parent = item.getParent();
		if (parent != null) {
			parent.getChildren().remove(item);
		}
		update = false;
	}

	private void removeNPC(final JSONObject npc) {
		final Alert deleteConfirmation = new Alert(AlertType.CONFIRMATION);
		deleteConfirmation.setTitle("NSC löschen?");
		deleteConfirmation.setHeaderText("NSC " + getName(npc) + " löschen?");
		deleteConfirmation.setContentText("Der NSC kann danach nicht wiederhergestellt werden!");
		deleteConfirmation.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

		final Optional<ButtonType> result = deleteConfirmation.showAndWait();
		if (result.isPresent() && result.get().equals(ButtonType.YES)) {
			ResourceManager.deleteResource(npc);
		}
	}

	private void setData() {
		update = true;

		name.setText(getName(actualNPC));

		final JSONObject basicValues = actualNPC.getObj("Basiswerte");

		final Energy lepEnergy = new Energy("Lebensenergie", new JSONObject(null), actualNPC);
		lep.getValueFactory().setValue(lepEnergy.getCurrent());
		lepMax.getValueFactory().setValue(lepEnergy.getMax());

		final Energy aupEnergy = new Energy("Ausdauer", new JSONObject(null), actualNPC);
		aup.getValueFactory().setValue(aupEnergy.getCurrent());
		aupMax.getValueFactory().setValue(aupEnergy.getMax());

		if (basicValues.containsKey("Astralenergie")) {
			final Energy aspEnergy = new Energy("Astralenergie", new JSONObject(null), actualNPC);
			asp.getValueFactory().setValue(aspEnergy.getCurrent());
			aspMax.getValueFactory().setValue(aspEnergy.getMax());
		}

		if (basicValues.containsKey("Karmaenergie")) {
			final Energy kapEnergy = new Energy("Karmaenergie", new JSONObject(null), actualNPC);
			kap.getValueFactory().setValue(kapEnergy.getCurrent());
			kapMax.getValueFactory().setValue(kapEnergy.getMax());
		}

		ko.getValueFactory().setValue(actualNPC.getObj("Eigenschaften").getObj("KO").getIntOrDefault("Wert", 0));
		ws.getValueFactory().setValue(HeroUtil.deriveValue(ResourceManager.getResource("data/Basiswerte").getObj("Wundschwelle"), actualNPC,
				actualNPC.getObj("Basiswerte").getObj("Wundschwelle"), false));

		wsBonus.setSelected(actualNPC.getObj("Vorteile").containsKey("Eisern"));
		wsMalus.setSelected(actualNPC.getObj("Nachteile").containsKey("Glasknochen"));
		tough.setSelected(actualNPC.getObj("Vorteile").containsKey("Zäher Hund"));

		update = false;
	}

	private void setName(final JSONObject npc, final String newName) {
		final JSONObject biography = npc.getObj("Biografie");
		biography.put("Name", newName);
		ResourceManager.moveResource(npc, "npcs/" + newName);
		biography.notifyListeners(npcListener);
	}

	private void setNPC(final JSONObject npc) {
		if (npc != null) {
			npc.getObj("Biografie").removeListener(npcListener);
			npc.getObj("Eigenschaften").getObj("KO").removeListener(npcListener);
			npc.getObj("Basiswerte").removeListener(npcListener);
		}

		actualNPC = npc;

		setData();

		basicValuesController.setCharacter(npc);
		attacksTable.setCharacter(npc);

		if (npc != null) {
			npc.getObj("Biografie").addListener(npcListener);
			npc.getObj("Eigenschaften").getObj("KO").addListener(npcListener);
			npc.getObj("Basiswerte").addListener(npcListener);
		}
	}
}
