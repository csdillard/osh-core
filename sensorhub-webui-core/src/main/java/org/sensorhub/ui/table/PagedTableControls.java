package org.sensorhub.ui.table;

import org.sensorhub.ui.api.UIConstants;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.v7.data.Property;
import com.vaadin.v7.data.Property.ValueChangeEvent;
import com.vaadin.v7.data.Property.ValueChangeListener;
import com.vaadin.v7.data.validator.IntegerRangeValidator;
import com.vaadin.v7.ui.ComboBox;
import com.vaadin.v7.ui.TextField;


public class PagedTableControls extends HorizontalLayout {

    private ComboBox itemsPerPageSelect = new ComboBox();
    private Label itemsPerPageLabel = new Label("Items per page:");
    //private Label pageLabel = new Label("Page:&nbsp;", ContentMode.HTML);
    private Button btnFirst = new Button("<<");
    private Button btnPrevious = new Button("<");
    private Button btnNext = new Button(">");
    private Button btnLast = new Button(">>");
    private TextField currentPageTextField = new TextField();

    @SuppressWarnings("deprecation")
    public PagedTableControls(final PagedTable table) {

        itemsPerPageSelect.addItem("5");
        itemsPerPageSelect.addItem("10");
        itemsPerPageSelect.addItem("25");
        itemsPerPageSelect.addItem("50");
        itemsPerPageSelect.setImmediate(true);
        itemsPerPageSelect.setNullSelectionAllowed(false);
        itemsPerPageSelect.setWidth(60, Unit.PIXELS);
        itemsPerPageSelect.select("10");
        itemsPerPageSelect.setStyleName(UIConstants.STYLE_SMALL);
        itemsPerPageLabel.setStyleName(UIConstants.STYLE_SMALL);
        
        currentPageTextField.setValue(String.valueOf(table.getCurrentPage()));
        currentPageTextField.setConverter(Integer.class);
        final IntegerRangeValidator validator = new IntegerRangeValidator("Wrong page number", 1, table.getTotalAmountOfPages());
        currentPageTextField.addValidator(validator);
        currentPageTextField.setWidth(50, Unit.PIXELS);
        currentPageTextField.setStyleName(UIConstants.STYLE_SMALL);
        currentPageTextField.setImmediate(true);
        
        Label separatorLabel = new Label("&nbsp;/&nbsp;", ContentMode.HTML);
        final Label totalPagesLabel = new Label(
                String.valueOf(table.getTotalAmountOfPages()), ContentMode.HTML);
        separatorLabel.setWidth(null);
        separatorLabel.setStyleName(UIConstants.STYLE_SMALL);
        totalPagesLabel.setWidth(null);
        totalPagesLabel.setStyleName(UIConstants.STYLE_SMALL);
        
        btnFirst.setStyleName(UIConstants.STYLE_LINK + " " + UIConstants.STYLE_SMALL);
        btnPrevious.setStyleName(UIConstants.STYLE_LINK + " " + UIConstants.STYLE_SMALL);
        btnNext.setStyleName(UIConstants.STYLE_LINK + " " + UIConstants.STYLE_SMALL);
        btnLast.setStyleName(UIConstants.STYLE_LINK + " " + UIConstants.STYLE_SMALL);
        
        //pageLabel.setWidth(null);
        HorizontalLayout pageSize = new HorizontalLayout();
        HorizontalLayout pageManagement = new HorizontalLayout();
        
        // add all action listeners
        itemsPerPageSelect.addValueChangeListener(new Property.ValueChangeListener() {
            private static final long serialVersionUID = 5215872810451266259L;

            public void valueChange(ValueChangeEvent event) {
                table.setPageLength(Integer.valueOf(String.valueOf(event
                        .getProperty().getValue())));
            }
        });
        
        currentPageTextField.addValueChangeListener(new ValueChangeListener() {
            private static final long serialVersionUID = -1301464754009535498L;

            public void valueChange(ValueChangeEvent event) {
                if (currentPageTextField.isValid()
                        && currentPageTextField.getValue() != null) {
                    int page = Integer.valueOf(String
                            .valueOf(currentPageTextField.getValue()));
                    table.setCurrentPage(page);
                }
            }
        });
        
        btnFirst.addClickListener(new Button.ClickListener() {
            private static final long serialVersionUID = -355520120491283992L;

            public void buttonClick(Button.ClickEvent event) {
                table.setCurrentPage(0);
            }
        });        
        
        btnPrevious.addClickListener(new Button.ClickListener() {
            private static final long serialVersionUID = -355520120491283992L;

            public void buttonClick(Button.ClickEvent event) {
                table.previousPage();
            }
        });
        
        btnNext.addClickListener(new Button.ClickListener() {
            private static final long serialVersionUID = -1927138212640638452L;

            public void buttonClick(Button.ClickEvent event) {
                table.nextPage();
            }
        });
        
        btnLast.addClickListener(new Button.ClickListener() {
            private static final long serialVersionUID = -355520120491283992L;

            public void buttonClick(Button.ClickEvent event) {
                table.setCurrentPage(table.getTotalAmountOfPages());
            }
        });

        pageSize.addComponent(itemsPerPageLabel);
        pageSize.addComponent(itemsPerPageSelect);
        pageSize.setComponentAlignment(itemsPerPageLabel, Alignment.MIDDLE_LEFT);
        pageSize.setComponentAlignment(itemsPerPageSelect, Alignment.MIDDLE_LEFT);
        pageSize.setSpacing(true);
        pageManagement.addComponent(btnFirst);
        pageManagement.addComponent(btnPrevious);
        //pageManagement.addComponent(pageLabel);
        pageManagement.addComponent(currentPageTextField);
        pageManagement.addComponent(separatorLabel);
        pageManagement.addComponent(totalPagesLabel);
        pageManagement.addComponent(btnNext);
        pageManagement.addComponent(btnLast);
        pageManagement.setComponentAlignment(btnFirst, Alignment.MIDDLE_LEFT);
        pageManagement.setComponentAlignment(btnPrevious, Alignment.MIDDLE_LEFT);
        //pageManagement.setComponentAlignment(pageLabel, Alignment.MIDDLE_LEFT);
        pageManagement.setComponentAlignment(currentPageTextField, Alignment.MIDDLE_LEFT);
        pageManagement.setComponentAlignment(separatorLabel, Alignment.MIDDLE_LEFT);
        pageManagement.setComponentAlignment(totalPagesLabel, Alignment.MIDDLE_LEFT);
        pageManagement.setComponentAlignment(btnNext, Alignment.MIDDLE_LEFT);
        pageManagement.setComponentAlignment(btnLast, Alignment.MIDDLE_LEFT);
        pageManagement.setWidth(null);
        pageManagement.setSpacing(true);

        addComponent(pageSize);
        addComponent(pageManagement);
        setComponentAlignment(pageManagement, Alignment.MIDDLE_CENTER);
        setWidth("100%");
        setExpandRatio(pageSize, 1);

        table.addListener(new PagedTable.PageChangeListener() {
            public void pageChanged(PagedTable.PagedTableChangeEvent event) {
                PagedTableContainer containerDataSource = (PagedTableContainer) table.getContainerDataSource();
                int startIndex = containerDataSource.getStartIndex();
                btnFirst.setEnabled(startIndex > 0);
                btnPrevious.setEnabled(startIndex > 0);
                int pageLength = table.getPageLength();
                btnNext.setEnabled(startIndex < containerDataSource.getRealSize() - pageLength);
                btnLast.setEnabled(startIndex < containerDataSource.getRealSize() - pageLength);
                int currentPage = table.getCurrentPage();
                currentPageTextField.setValue(String.valueOf(currentPage));
                int totalAmountOfPages = table.getTotalAmountOfPages();
                totalPagesLabel.setValue(String.valueOf(totalAmountOfPages));
                itemsPerPageSelect.setValue(String.valueOf(pageLength));
                validator.setMaxValue(totalAmountOfPages);
            }
        });
    }

    public ComboBox getItemsPerPageSelect() {
        return itemsPerPageSelect;
    }

    public Label getItemsPerPageLabel() {
        return itemsPerPageLabel;
    }

    /*public Label getPageLabel() {
        //return pageLabel;
    }*/

    public Button getBtnFirst() {
        return btnFirst;
    }

    public Button getBtnPrevious() {
        return btnPrevious;
    }

    public Button getBtnNext() {
        return btnNext;
    }

    public Button getBtnLast() {
        return btnLast;
    }

    public TextField getCurrentPageTextField() {
        return currentPageTextField;
    }
}
